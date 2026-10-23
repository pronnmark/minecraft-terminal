#!/bin/sh
# Minecraft Terminal installer (macOS, Linux). Run it again to update.
#   curl -fsSL https://github.com/pronnmark/minecraft-terminal/releases/latest/download/install.sh | sh
#
# Installs Fabric with Fabric's own installer, then puts Fabric API and Minecraft Terminal in the
# mods folder. Every download is checked against the hash pinned when this release was built.
set -eu

MC_VERSION="@MC_VERSION@"
LOADER_VERSION="@LOADER_VERSION@"
MOD_URL="@MOD_URL@"
MOD_SHA512="@MOD_SHA512@"
API_URL="@API_URL@"
API_SHA512="@API_SHA512@"
INSTALLER_URL="https://maven.fabricmc.net/net/fabricmc/fabric-installer/1.1.2/fabric-installer-1.1.2.jar"
INSTALLER_SHA512="3cbe7b69498c44d814fc95e71cd9ce9c12c45a9e1dcf01085cb3e9aaf6fe63134b701065167be1fa6e4c2df19dfa5400bc5b6b1b7fb5e99e334d24dcdca55c20"

say() { printf '%s\n' "$*"; }
die() { printf 'Minecraft Terminal: %s\n' "$*" >&2; exit 1; }

case "$(uname -s)" in
	Darwin) MC_DIR="$HOME/Library/Application Support/minecraft"
		MODRINTH="$HOME/Library/Application Support/ModrinthApp" ;;
	Linux) MC_DIR="$HOME/.minecraft"
		MODRINTH="$HOME/.local/share/ModrinthApp" ;;
	*) die "only macOS and Linux are supported" ;;
esac

sha512() { if command -v sha512sum >/dev/null 2>&1; then sha512sum "$1"; else shasum -a 512 "$1"; fi | cut -d' ' -f1; }

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

# fetch <url> <sha512> <file>
fetch() {
	curl -fsSL --retry 3 -o "$TMP/$3" "$1" || die "could not download $1"
	[ "$(sha512 "$TMP/$3")" = "$2" ] || die "$3 is damaged (hash mismatch); try again"
}

say "Downloading Minecraft Terminal and Fabric API..."
MOD_FILE=$(basename "$MOD_URL")
API_FILE=$(basename "$API_URL" | sed 's/%2B/+/g')
fetch "$MOD_URL" "$MOD_SHA512" "$MOD_FILE"
fetch "$API_URL" "$API_SHA512" "$API_FILE"

# put_mods <mods dir>: replace any older copies, keep everything else.
put_mods() {
	mkdir -p "$1"
	rm -f "$1"/minecraft-terminal-*.jar "$1"/fabric-api-*.jar
	cp "$TMP/$MOD_FILE" "$TMP/$API_FILE" "$1/"
}

installed=""

# The official Minecraft Launcher: add Fabric with Fabric's installer, using Minecraft's own Java.
if [ -f "$MC_DIR/launcher_profiles.json" ]; then
	JAVA=$(find "$MC_DIR/runtime" -type f -path '*/bin/java' 2>/dev/null | sort | tail -n 1)
	[ -n "$JAVA" ] || JAVA=$(command -v java || true)
	[ -n "$JAVA" ] || die "no Java found; open the Minecraft Launcher, play $MC_VERSION once, then run this again"
	fetch "$INSTALLER_URL" "$INSTALLER_SHA512" fabric-installer.jar
	say "Adding Fabric to the Minecraft Launcher..."
	"$JAVA" -jar "$TMP/fabric-installer.jar" client -dir "$MC_DIR" -mcversion "$MC_VERSION" \
		-loader "$LOADER_VERSION" >"$TMP/fabric.log" 2>&1 || { cat "$TMP/fabric.log" >&2; die "Fabric install failed"; }
	put_mods "$MC_DIR/mods"
	installed="Minecraft Launcher: pick \"fabric-loader-$MC_VERSION\" next to Play"
fi

# The Modrinth App: every Fabric $MC_VERSION profile gets the mods too.
if [ -f "$MODRINTH/app.db" ] && command -v sqlite3 >/dev/null 2>&1; then
	sqlite3 "$MODRINTH/app.db" "select i.path from instances i join instance_content_sets s on s.id = i.applied_content_set_id where s.game_version = '$MC_VERSION' and s.loader = 'fabric'" 2>/dev/null |
		while IFS= read -r p; do [ -n "$p" ] && put_mods "$MODRINTH/profiles/$p/mods" && say "Added to Modrinth profile: $p"; done
	[ -n "$installed" ] || installed="Modrinth App: play your Fabric $MC_VERSION profile"
fi

[ -n "$installed" ] || die "Minecraft not found. Install the Minecraft Launcher (minecraft.net), sign in and open it once, then run this again."

say ""
say "Done. Open Minecraft and press Play ($installed)."
say "In game, press K for a terminal."
