/* Bundled local UI. Privileged actions use native per-window bindings only. */
const $ = (id) => document.getElementById(id);
let model, folder, busy = false;
const api = () => globalThis.bindings ?? globalThis.receiverPreview;
function message(text) {
  $("message").textContent = text ?? "";
  $("message").hidden = !text;
}
async function action(fn) {
  if (busy) return;
  busy = true;
  document.body.classList.add("busy");
  try {
    message(null);
    const result = await fn();
    if (result?.status) render(result);
  } catch (error) {
    message(error?.message ?? String(error));
  } finally {
    busy = false;
    document.body.classList.remove("busy");
  }
}
function text(tag, value, className) {
  const e = document.createElement(tag);
  e.textContent = value;
  if (className) e.className = className;
  return e;
}
function render(state) {
  model = state;
  const ready = state.status === "ready";
  $("status-dot").className = "dot " +
    (ready ? "ready" : state.status === "error" ? "error" : "");
  $("status-title").textContent = ready
    ? "Ready to receive"
    : state.status === "starting"
    ? "Starting receiver…"
    : state.status === "pausing"
    ? "Finishing transfers…"
    : state.status === "error"
    ? "Connection needs attention"
    : "Receiver paused";
  $("status-subtitle").textContent = state.active
    ? `${state.active} ${
      state.active === 1 ? "photo is" : "photos are"
    } arriving`
    : ready
    ? "Photos stay organized, automatically"
    : state.error ?? "Your photos and pairing are kept safe";
  $("power").textContent = ready ? "Pause" : "Start";
  $("power").disabled = !state.initialized ||
    ["starting", "pausing"].includes(state.status);
  $("root").textContent = state.root ??
    "Choose your existing capture folder to continue.";
  $("folder-name").textContent = state.root
    ? state.root.replace(/[\\/]+$/, "").split(/[\\/]/).pop()
    : "Choose a folder";
  $("active").textContent = state.active
    ? `${state.active} ${
      state.active === 1 ? "transfer" : "transfers"
    } in progress`
    : "No transfers in progress";
  $("connection").textContent = state.endpoint
    ? `${state.endpoint} · Encrypted transfer`
    : "Local storage · Encrypted transfer";
  $("qr").hidden = !state.qr;
  $("qr-empty").hidden = !!state.qr;
  if (state.qr) $("qr").src = "data:image/svg+xml;base64," + btoa(state.qr);
  $("qr-hint").textContent = !ready
    ? "Start receiving to pair a phone."
    : state.devices?.length
    ? "Your phone is paired. Add another when needed."
    : "Create a code to connect your phone.";
  $("pair").disabled = !ready;
  $("pair").textContent = state.pairing
    ? "Refresh pairing code"
    : state.devices?.length
    ? "Pair another phone"
    : "Create pairing code";
  $("expiry").textContent = state.pairing
    ? `Single-use code · expires in ${
      Math.max(0, Math.ceil((state.pairing.expiresAt - Date.now()) / 60000))
    } min`
    : "Your phone and computer must use the same local network.";
  $("phone-count").textContent = `${state.devices?.length ?? 0} ${
    state.devices?.length === 1 ? "phone" : "phones"
  }`;
  $("devices").replaceChildren();
  if (!state.devices?.length) {
    $("devices").append(
      text("p", "Your paired phones will appear here.", "muted"),
    );
  }
  for (const device of state.devices ?? []) {
    const row = text("div", "", "device"),
      info = text("div", "", "device-text");
    info.append(
      text("strong", device.name || "Phone"),
      text(
        "small",
        "Paired securely · " + new Date(device.createdAt).toLocaleDateString(),
      ),
    );
    const revoke = text("button", "Disconnect", "quiet");
    revoke.onclick = () => {
      if (
        confirm(
          `Disconnect ${
            device.name || "this phone"
          }? Saved photos stay where they are.`,
        )
      ) action(() => api().revoke(device.id));
    };
    row.append(text("span", "▣", "arrival-icon"), info, revoke);
    $("devices").append(row);
  }
  if (state.recent?.length) {
    $("arrivals").replaceChildren();
    for (const photo of state.recent) {
      const row = text("div", "", "arrival"),
        info = text("div", "", "arrival-text");
      info.append(
        text("strong", photo.filename),
        text("small", photo.relativePath),
      );
      row.append(
        text("span", "✓", "arrival-icon"),
        info,
        text(
          "time",
          new Date(photo.receivedAt).toLocaleTimeString([], {
            hour: "2-digit",
            minute: "2-digit",
          }),
        ),
      );
      $("arrivals").append(row);
    }
  }
  $("autostart").checked = !!state.autostart;
  $("close-to-tray").checked = !!state.closeToTray;
  $("tray-setting").hidden = !state.tray;
  $("open").disabled = !state.root;
  if (state.error && !busy) message(state.error);
}
async function browse(path) {
  const result = await api().browseFolders(path);
  folder = result.path;
  $("browse-path").value = folder;
  $("parent").disabled = result.parent === folder;
  $("parent").onclick = () => action(() => browse(result.parent));
  $("folder-list").replaceChildren();
  for (const child of result.folders) {
    const button = text("button", "▱  " + child.name, "folder-row");
    button.onclick = () => action(() => browse(child.path));
    $("folder-list").append(button);
  }
  if (!result.folders.length) {
    $("folder-list").append(
      text("p", "No subfolders here. You can use this folder.", "muted"),
    );
  }
}
$("choose").onclick = () =>
  action(async () => {
    const result = await api().chooseFolder();
    if (result?.fallback) {
      await browse(model.root ?? undefined);
      $("folder-dialog").showModal();
    } else if (result?.status) return result;
  });
$("browse-go").onclick = () => action(() => browse($("browse-path").value));
$("browse-path").onkeydown = (event) => {
  if (event.key === "Enter") {
    event.preventDefault();
    action(() => browse($("browse-path").value));
  }
};
$("use-folder").onclick = () =>
  action(async () => {
    const result = await api().setRoot(folder);
    $("folder-dialog").close();
    return result;
  });
$("open").onclick = () => action(() => api().openFolder());
$("pair").onclick = () => action(() => api().pair());
$("power").onclick = () =>
  action(() => model.status === "ready" ? api().stop() : api().start());
$("settings-button").onclick = () => $("settings-dialog").showModal();
$("autostart").onchange = () =>
  action(() => api().setAutostart($("autostart").checked));
$("close-to-tray").onchange = () =>
  action(() => api().setCloseToTray($("close-to-tray").checked));
$("quit").onclick = () => action(() => api().quit());
async function refresh() {
  try {
    if (api()) render(await api().snapshot());
  } catch (e) {
    message(e?.message ?? "Receiver connection unavailable");
  }
}
refresh();
setInterval(refresh, 1500);

$("licenses").onclick = () =>
  action(async () => {
    $("licenses-text").textContent = await (await fetch("licenses.txt")).text();
    $("licenses-dialog").showModal();
  });
