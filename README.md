<div align="center">

<img src="https://capsule-render.vercel.app/api?type=rect&color=0:0b1220,100:0e7490&height=110&section=header&text=RandomSpawn&fontSize=42&fontColor=22d3ee&fontAlignY=54&desc=Random%20safe%20spawns%20and%20an%20/rtp%20command&descSize=13&descColor=94a3b8&descAlignY=80" width="100%" alt="RandomSpawn" />

<p>
<img src="https://img.shields.io/github/v/release/chizzar-dev/RandomSpawn?style=flat&label=release&color=06b6d4&labelColor=0b1220" alt="release" />
<img src="https://img.shields.io/badge/Minecraft-1.8%20%E2%80%93%201.21.11-0891b2?style=flat&labelColor=0b1220" alt="Minecraft 1.8 - 1.21.11" />
<img src="https://img.shields.io/badge/Java-8%2B-155e75?style=flat&labelColor=0b1220&logo=openjdk&logoColor=22d3ee" alt="Java 8+" />
<a href="LICENSE"><img src="https://img.shields.io/github/license/chizzar-dev/RandomSpawn?style=flat&label=license&color=0e7490&labelColor=0b1220" alt="license" /></a>
</p>

</div>

RandomSpawn places new players at a random safe location and adds an /rtp command with warmup and cooldown. The search is spread across ticks to avoid chunk-load lag.

*RandomSpawn, yeni oyunculari rastgele guvenli bir noktaya yerlestirir ve bekleme sureli bir /rtp komutu ekler. Arama tick'lere bolunerek yapilir, chunk yuklemesi sunucuyu kilitlemez.*

## Features · Özellikler
- İlk girişte ve yeniden doğuşta **rastgele güvenli konum**
- **`/rtp` komutu** — bekleme süresi, geri sayım ve bekleme (cooldown) desteğiyle
- Beklerken hareket edilirse ışınlanma iptal olur
- Güvenlik kontrolü: lav, ateş, kaktüs, magma, portal ve kar kuyusunun üstüne ışınlamaz
- İsteğe bağlı olarak suyun üstüne ışınlamayı engeller
- **Nether desteği** — tavanı olan dünyalarda aşağı doğru tarayarak hava boşluğu bulur
- Arama **tick'lere bölünerek** yapılır; chunk yükleme sunucuyu kilitlemez
- En az uzaklık ayarı ile spawn çevresine düşmeyi engeller
- Yatak spawn'ı korunur (istersen zorlayabilirsin)

## Installation · Kurulum
1. [Releases](https://github.com/chizzar-dev/RandomSpawn/releases/latest) sayfasından `RandomSpawn.jar` dosyasını indir.
2. Sunucunun `plugins/` klasörüne at.
3. Sunucuyu yeniden başlat.
4. `plugins/RandomSpawn/config.yml` dosyasından merkez ve yarıçapı ayarla.

## Commands · Komutlar
| Komut | Açıklama | Yetki |
|-------|----------|-------|
| `/rtp` | Rastgele güvenli bir yere ışınlanır | `randomspawn.rtp` |
| `/rtp <oyuncu>` | Başka bir oyuncuyu ışınlar | `randomspawn.rtp.others` |
| `/randomspawn reload` | Ayarları yeniden yükler | `randomspawn.reload` |

**Alias:** `/randomtp` · `/rastgele` · `/rs`

## Permissions · Yetkiler
| Yetki | Açıklama | Varsayılan |
|-------|----------|------------|
| `randomspawn.rtp` | `/rtp` kullanabilir | herkes |
| `randomspawn.rtp.bypass` | Bekleme süresi ve cooldown olmadan ışınlanır | op |
| `randomspawn.rtp.others` | Başkalarını ışınlayabilir | op |
| `randomspawn.reload` | Configi yeniden yükler | op |

## Configuration · Ayarlar
| Anahtar | Açıklama |
|---------|----------|
| `world` | Hangi dünya (boş = ana dünya) |
| `center-x` / `center-z` | Merkez koordinatı |
| `radius` / `min-radius` | En fazla ve en az uzaklık |
| `max-attempts` | Toplam deneme sayısı |
| `attempts-per-tick` | Her tick'te kaç deneme (düşük = daha az yük) |
| `avoid-water` | Suyun üstüne ışınlamayı engelle |
| `unsafe-blocks` | Ek güvensiz bloklar |
| `nether-scan-from` / `nether-scan-to` | Nether tarama aralığı |
| `first-join` / `respawn` | İlk giriş ve yeniden doğuşta çalışsın mı |
| `override-bed` | Yatak spawn'ını yok say |
| `rtp.warmup` / `cooldown` | Bekleme süresi ve iki kullanım arası bekleme |
| `rtp.cancel-on-move` | Hareket edince iptal |
| `rtp.use-current-world` | Oyuncunun bulunduğu dünyada ışınlansın |

## Building · Derleme
```bash
mvn clean package
```
Çıktı · Output: `target/RandomSpawn.jar`

Her push [GitHub Actions](https://github.com/chizzar-dev/RandomSpawn/actions/workflows/build.yml) ile derlenir; `v*` etiketli sürümler jar'la birlikte [Releases](https://github.com/chizzar-dev/RandomSpawn/releases) sayfasına eklenir.
<br><sub>Every push is built by GitHub Actions; tagged `v*` releases attach the jar.</sub>

## License · Lisans
[MIT](LICENSE) — istediğin gibi kullan, değiştir, dağıt · use, modify and distribute freely

<div align="center"><sub>chizzar-dev · Minecraft plugins for 1.8 – 1.21.11 · <a href="https://discord.gg/forges">Discord</a></sub></div>
