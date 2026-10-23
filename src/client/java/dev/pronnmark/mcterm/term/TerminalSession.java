package dev.pronnmark.mcterm.term;

import com.jediterm.core.typeahead.TerminalTypeAheadManager;
import com.jediterm.core.typeahead.TypeAheadTerminalModel;
import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.CursorShape;
import com.jediterm.terminal.ProcessTtyConnector;
import com.jediterm.terminal.RequestOrigin;
import com.jediterm.terminal.StyledTextConsumer;
import com.jediterm.terminal.TerminalDisplay;
import com.jediterm.terminal.TerminalExecutorServiceManager;
import com.jediterm.terminal.TerminalStarter;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.TtyBasedArrayDataStream;
import com.jediterm.terminal.emulator.mouse.MouseEventProcessingSettings;
import com.jediterm.terminal.emulator.mouse.MouseFormat;
import com.jediterm.terminal.emulator.mouse.MouseMode;
import com.jediterm.terminal.model.CharBuffer;
import com.jediterm.terminal.model.JediTerminal;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalSelection;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.WinSize;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One pseudo-terminal running a command, emulated by JediTerm. Independent of Minecraft, so the
 * process keeps running (and its output keeps being consumed) while the terminal screen is closed.
 */
public final class TerminalSession implements TerminalDisplay {
	private static final Logger LOG = LoggerFactory.getLogger("mcterm");
	private static final int HISTORY_LINES = 5000;

	/** A run of same-styled cells. {@code text} has one char per cell (JediTerm's model). */
	public record Run(int col, int row, String text, TextStyle style, boolean blank) {}

	public record Snapshot(List<Run> runs, int cursorCol, int cursorRow, boolean cursorVisible,
						   CursorShape cursorShape, int historyLines) {}

	private final List<String> command;
	private final Map<String, String> extraEnv;
	private final String startKeys;
	private volatile long lastOutputNanos;

	private volatile PtyProcess process;
	private volatile JediTerminal terminal;
	private volatile TerminalTextBuffer buffer;
	private volatile TerminalStarter starter;
	private volatile Executors2 executors;

	private volatile int cursorX;
	private volatile int cursorY = 1;
	private volatile boolean cursorVisible = true;
	private volatile CursorShape cursorShape;
	private volatile String title = "";
	private volatile MouseMode mouseMode = MouseMode.MOUSE_REPORTING_NONE;
	private volatile boolean bracketedPaste;
	private volatile boolean running;
	private volatile Integer exitCode;
	private volatile String startError;
	private int cols;
	private int rows;

	public TerminalSession(List<String> command, Map<String, String> extraEnv) {
		this(command, extraEnv, "");
	}

	/** {@code startKeys} are typed once the program has drawn its first screen, e.g. to pick a herdr space. */
	public TerminalSession(List<String> command, Map<String, String> extraEnv, String startKeys) {
		this.command = List.copyOf(command);
		this.extraEnv = Map.copyOf(extraEnv);
		this.startKeys = startKeys == null ? "" : startKeys;
	}

	public synchronized boolean isStarted() {
		return starter != null || startError != null;
	}

	public boolean isRunning() {
		return running;
	}

	public @Nullable Integer exitCode() {
		return exitCode;
	}

	public @Nullable String startError() {
		return startError;
	}

	public List<String> command() {
		return command;
	}

	public synchronized void start(int columns, int lines) {
		close();
		cols = columns;
		rows = lines;
		exitCode = null;
		startError = null;
		cursorX = 0;
		cursorY = 1;
		cursorVisible = true;
		cursorShape = null;
		mouseMode = MouseMode.MOUSE_REPORTING_NONE;
		bracketedPaste = false;

		StyleState styleState = new StyleState();
		TerminalTextBuffer textBuffer = new TerminalTextBuffer(columns, lines, styleState, HISTORY_LINES);
		JediTerminal term = new JediTerminal(this, textBuffer, styleState);

		Map<String, String> env = new HashMap<>(System.getenv());
		env.put("TERM", "xterm-256color");
		env.put("COLORTERM", "truecolor");
		env.put("TERM_PROGRAM", "mcterm");
		env.putIfAbsent("LANG", "en_US.UTF-8");
		env.putAll(extraEnv);

		PtyProcess proc;
		try {
			proc = new PtyProcessBuilder(command.toArray(String[]::new))
					.setEnvironment(env)
					.setDirectory(System.getProperty("user.home"))
					.setInitialColumns(columns)
					.setInitialRows(lines)
					.setConsole(false)
					.start();
		} catch (IOException | RuntimeException e) {
			LOG.error("Failed to start {}", command, e);
			startError = e.toString();
			buffer = textBuffer;
			terminal = term;
			return;
		}

		Executors2 exec = new Executors2();
		lastOutputNanos = 0;
		PtyConnector connector = new PtyConnector(proc, command, this);
		TerminalTypeAheadManager typeAhead = new TerminalTypeAheadManager(new NoTypeAhead(columns));
		TerminalStarter st = new TerminalStarter(term, connector,
				new TtyBasedArrayDataStream(connector, typeAhead::onTerminalStateChanged), typeAhead, exec);

		process = proc;
		buffer = textBuffer;
		terminal = term;
		executors = exec;
		starter = st;
		running = true;

		Thread emulator = new Thread(() -> {
			try {
				st.start();
			} catch (Throwable t) {
				LOG.warn("Terminal emulator stopped", t);
			} finally {
				try {
					exitCode = proc.waitFor();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				if (starter == st) {
					running = false;
				}
			}
		}, "mcterm-emulator");
		emulator.setDaemon(true);
		emulator.start();
		if (!startKeys.isEmpty()) typeStartKeys(st);
		LOG.info("Started {} ({}x{})", command, columns, lines);
	}

	/** Waits until the program has drawn its first screen, then types the keys one by one. */
	private void typeStartKeys(TerminalStarter st) {
		Thread typer = new Thread(() -> {
			try {
				// Ready = quiet for 500 ms, or 2 s after the first output (live UIs such as herdr animate forever).
				long deadline = System.nanoTime() + 20_000_000_000L;
				long first = 0;
				while (starter == st && System.nanoTime() < deadline) {
					long last = lastOutputNanos;
					long now = System.nanoTime();
					if (last != 0 && first == 0) first = now;
					if (last != 0 && (now - last > 500_000_000L || now - first > 2_000_000_000L)) break;
					Thread.sleep(50);
				}
				for (int i = 0; i < startKeys.length() && starter == st && running; ) {
					int cp = startKeys.codePointAt(i);
					st.sendString(new String(Character.toChars(cp)), true);
					i += Character.charCount(cp);
					// The first key usually opens a picker; give it time to appear.
					Thread.sleep(i == Character.charCount(startKeys.codePointAt(0)) ? 400 : 60);
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}, "mcterm-startkeys");
		typer.setDaemon(true);
		typer.start();
	}

	public synchronized void resize(int columns, int lines) {
		if (columns == cols && lines == rows) return;
		cols = columns;
		rows = lines;
		TerminalStarter st = starter;
		if (st != null) {
			st.postResize(new TermSize(columns, lines), RequestOrigin.User);
		}
	}

	public synchronized void close() {
		TerminalStarter st = starter;
		starter = null;
		running = false;
		if (st != null) {
			st.requestEmulatorStop();
			st.close();
		}
		PtyProcess proc = process;
		process = null;
		if (proc != null && proc.isAlive()) {
			proc.destroy();
		}
		Executors2 exec = executors;
		executors = null;
		if (exec != null) {
			exec.shutdownWhenAllExecuted();
		}
	}

	// ---- input ----

	public void sendString(String s) {
		TerminalStarter st = starter;
		if (st != null && running && !s.isEmpty()) st.sendString(s, true);
	}

	public void sendBytes(byte[] b) {
		TerminalStarter st = starter;
		if (st != null && running && b != null && b.length > 0) st.sendBytes(b, true);
	}

	/** Key code from JediTerm's encoder (respects application cursor/keypad modes). */
	public byte @Nullable [] codeForKey(int vk, int modifiersEx) {
		JediTerminal term = terminal;
		return term == null ? null : term.getCodeForKey(vk, modifiersEx);
	}

	public void paste(String text) {
		String normalized = text.replace("\r\n", "\r").replace('\n', '\r');
		if (bracketedPaste) {
			normalized = "\u001b[200~" + normalized.replace("\u001b[201~", "") + "\u001b[201~";
		}
		sendString(normalized);
	}

	/** Forwards a mouse event; returns false when the application did not want it. */
	public boolean mouseEvent(int col, int row, com.jediterm.core.input.MouseEvent event) {
		JediTerminal term = terminal;
		TerminalTextBuffer buf = buffer;
		if (term == null || buf == null || !running) return false;
		var settings = new MouseEventProcessingSettings(true, buf.isUsingAlternateBuffer(), true);
		return term.onMouseEvent(col, row, event, settings);
	}

	public boolean mouseReporting() {
		return mouseMode != MouseMode.MOUSE_REPORTING_NONE;
	}

	public boolean usingAlternateBuffer() {
		TerminalTextBuffer buf = buffer;
		return buf != null && buf.isUsingAlternateBuffer();
	}

	public String title() {
		return title;
	}

	// ---- snapshot for rendering ----

	public @Nullable Snapshot snapshot(int scrollBack) {
		TerminalTextBuffer buf = buffer;
		if (buf == null) return null;
		List<Run> runs = new ArrayList<>(256);
		int history;
		int back;
		buf.lock();
		try {
			history = buf.getHistoryLinesCount();
			int visibleRows = buf.getHeight();
			back = Math.max(0, Math.min(scrollBack, history));
			buf.processHistoryAndScreenLines(-back, visibleRows, new StyledTextConsumer() {
				@Override
				public void consume(int x, int y, @NotNull TextStyle style, @NotNull CharBuffer chars, int startRow) {
					runs.add(new Run(x, y - startRow, chars.toString(), style, false));
				}

				@Override
				public void consumeNul(int x, int y, int nulIndex, @NotNull TextStyle style, @NotNull CharBuffer chars, int startRow) {
					runs.add(new Run(x, y - startRow, " ".repeat(chars.length()), style, true));
				}

				@Override
				public void consumeQueue(int x, int y, int nulIndex, int startRow) {
				}
			});
		} finally {
			buf.unlock();
		}
		return new Snapshot(runs, cursorX, cursorY - 1 + back, cursorVisible && running, cursorShape, history);
	}

	/** Plain text of the visible screen, one line per row. */
	public String screenText() {
		TerminalTextBuffer buf = buffer;
		if (buf == null) return "";
		buf.lock();
		try {
			return buf.getScreenLines();
		} finally {
			buf.unlock();
		}
	}

	// ---- TerminalDisplay ----

	@Override
	public void setCursor(int x, int y) {
		cursorX = x;
		cursorY = y;
	}

	@Override
	public void setCursorShape(@Nullable CursorShape shape) {
		cursorShape = shape;
	}

	@Override
	public void beep() {
	}

	@Override
	public void scrollArea(int scrollRegionTop, int scrollRegionSize, int dy) {
	}

	@Override
	public void setCursorVisible(boolean visible) {
		cursorVisible = visible;
	}

	@Override
	public void useAlternateScreenBuffer(boolean useAlternateScreenBuffer) {
	}

	@Override
	public String getWindowTitle() {
		return title;
	}

	@Override
	public void setWindowTitle(@NotNull String windowTitle) {
		title = windowTitle;
	}

	@Override
	public @Nullable TerminalSelection getSelection() {
		return null;
	}

	@Override
	public void terminalMouseModeSet(@NotNull MouseMode mode) {
		mouseMode = mode;
	}

	@Override
	public void setMouseFormat(@NotNull MouseFormat mouseFormat) {
	}

	@Override
	public boolean ambiguousCharsAreDoubleWidth() {
		return false;
	}

	@Override
	public void setBracketedPasteMode(boolean enabled) {
		bracketedPaste = enabled;
	}

	// ---- plumbing ----

	private static final class PtyConnector extends ProcessTtyConnector {
		private final PtyProcess pty;
		private final TerminalSession session;

		PtyConnector(PtyProcess pty, List<String> command, TerminalSession session) {
			super(pty, StandardCharsets.UTF_8, command);
			this.pty = pty;
			this.session = session;
		}

		@Override
		public int read(char[] buf, int offset, int length) throws IOException {
			int n = super.read(buf, offset, length);
			if (n > 0) session.lastOutputNanos = System.nanoTime();
			return n;
		}

		@Override
		public String getName() {
			return "mcterm";
		}

		@Override
		public void resize(@NotNull TermSize size) {
			if (pty.isAlive()) {
				pty.setWinSize(new WinSize(size.getColumns(), size.getRows()));
			}
		}
	}

	private static final class Executors2 implements TerminalExecutorServiceManager {
		private final ScheduledExecutorService single = Executors.newSingleThreadScheduledExecutor(daemon("mcterm-io"));
		private final ExecutorService unbounded = Executors.newCachedThreadPool(daemon("mcterm-worker"));

		@Override
		public @NotNull ScheduledExecutorService getSingleThreadScheduledExecutor() {
			return single;
		}

		@Override
		public @NotNull ExecutorService getUnboundedExecutorService() {
			return unbounded;
		}

		@Override
		public void shutdownWhenAllExecuted() {
			single.shutdown();
			unbounded.shutdown();
		}

		private static ThreadFactory daemon(String prefix) {
			AtomicInteger n = new AtomicInteger();
			return r -> {
				Thread t = new Thread(r, prefix + "-" + n.incrementAndGet());
				t.setDaemon(true);
				return t;
			};
		}
	}

	/** Type-ahead prediction is an IDE latency trick; disabled here. */
	private record NoTypeAhead(int width) implements TypeAheadTerminalModel {
		@Override public void insertCharacter(char ch, int index) {}
		@Override public void removeCharacters(int from, int count) {}
		@Override public void moveCursor(int index) {}
		@Override public void forceRedraw() {}
		@Override public void clearPredictions() {}
		@Override public void lock() {}
		@Override public void unlock() {}
		@Override public boolean isUsingAlternateBuffer() { return false; }
		@Override public @NotNull LineWithCursorX getCurrentLineWithCursor() { return new LineWithCursorX(new StringBuffer(), 0); }
		@Override public int getTerminalWidth() { return width; }
		@Override public boolean isTypeAheadEnabled() { return false; }
		@Override public long getLatencyThreshold() { return Long.MAX_VALUE; }
		@Override public ShellType getShellType() { return ShellType.Unknown; }
	}
}
