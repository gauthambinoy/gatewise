fn main() {
    // Generates the Tauri context (parses tauri.conf.json, bundles assets, wires up
    // the capability/permission set) at compile time. Required for any Tauri v2 app.
    tauri_build::build()
}
