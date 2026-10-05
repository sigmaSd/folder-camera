(async () => {
  const response = await fetch('downloads.json');
  if (!response.ok) return;
  const data = await response.json();
  if (!data.receiver?.available) return;
  const area = document.getElementById('receiver-downloads');
  const platform = /Windows/i.test(navigator.userAgent) ? 'windows' : /Macintosh|Mac OS X/i.test(navigator.userAgent) ? 'macos' : 'linux';
  area.replaceChildren();
  const assets = [...data.receiver.assets].sort((a,b) => Number(b.platform.startsWith(platform))-Number(a.platform.startsWith(platform)));
  for (const asset of assets) {
    const url = new URL(asset.url);
    if (url.origin !== 'https://github.com' || !url.pathname.startsWith('/sigmasd/folder-camera/releases/download/')) continue;
    const link = document.createElement('a');link.className='button '+(asset.platform.startsWith(platform)?'':'secondary');link.href=url.href;link.textContent=asset.label;area.append(link);
  }
  const notes = document.createElement('p');notes.className='fine';notes.textContent='Windows/macOS preview builds may show publisher trust prompts. Choose the build for your computer architecture.';area.after(notes);
})().catch(() => {});
