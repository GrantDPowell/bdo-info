const sharp = require('sharp');
const fs = require('fs');
const path = require('path');
const src = path.join(__dirname, 'webp_v2');
const out = path.join(__dirname, '..', '..', 'app', 'src', 'main', 'assets', 'item_icons');
(async () => {
  for (const f of fs.readdirSync(src).filter(f => f.endsWith('.webp'))) {
    const dest = path.join(out, f.replace('.webp', '.png'));
    await sharp(path.join(src, f)).resize(44, 44).png().toFile(dest);
    console.log(f, '->', dest);
  }
})();
