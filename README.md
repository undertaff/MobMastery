<p align="center">
  <b>English</b> | <a href="#türkçe">Türkçe</a>
</p>

# MobMastery

**Leveled mobs, tool skills with abilities, and a built-in anti-lag system, all in one lightweight Spigot/Paper plugin.**

Built against the Spigot API **1.21.10**. This is a first release, so please report any bug you find.

## English

### Table of contents
- [Features](#features)
- [How leveled mobs work](#how-leveled-mobs-work)
- [How tool skills work](#how-tool-skills-work)
- [Anti-lag system](#anti-lag-system)
- [Languages](#languages)
- [Commands and permissions](#commands-and-permissions)
- [Installation](#installation)
- [Configuration](#configuration)
- [Building from source](#building-from-source)
- [Notes](#notes)

### Features
- **Leveled mobs (level 1-60).** Only some hostile mobs get a level, the rest stay normal. Higher levels mean tougher mobs and better drops.
- **Tool skills (level 1-80).** Sword, axe, pickaxe and shovel each have their own level, XP bar, rank title and unlockable abilities.
- **On-screen messages** when you level up or unlock a new ability.
- **Anti-lag.** Slow timers only, with no per-tick work. Trims overcrowded chunks, clears old ground items with a countdown, pauses natural spawns when the server struggles and reacts to high memory use.
- **Two languages.** English (default) and Turkish. Every player chooses their own language.
- **Everything is configurable** in `config.yml`.

### How leveled mobs work
- Hostile mobs (zombies, skeletons, spiders and similar) have a chance to spawn leveled. Default: 50%.
- **Level formula (defaults):** `1 + (distance from world spawn / 100)` plus a small random spread of -2 to +4. The Nether adds +15 and the End adds +30. The result is limited to 1-60.
- A leveled mob shows its level in its name, for example `[Lv.23] Zombie`.
- **Strength scales with level (defaults):** each level adds about 10% effective health (the mob takes less damage) and about 2.5% extra damage to players.
- **Drops come in four tiers:** levels 1-15, 16-30, 31-45 and 46-60. Higher tiers give better items and more experience.
- Every tier also has a small chance of **rare drops**, such as diamonds, totems of undying, netherite scraps, enchanted golden apples and, in the top tier, netherite ingots. You get a message when a rare item drops.
- By default, extra drops only appear when a **player** kills the mob.
- Boss mobs are never leveled. You can exclude other mob types or whole worlds in the config.

### How tool skills work
Each of the four skills has its own level from 1 to 80.

| Skill | XP comes from |
|---|---|
| Sword | Killing mobs while holding a sword |
| Axe | Killing mobs and chopping logs |
| Pickaxe | Mining ores and stone |
| Shovel | Digging dirt, sand, gravel, clay and similar blocks |

- Higher-level mobs give more XP. Blocks placed by players give no XP, which stops place-and-break farming.
- **XP needed for the next level (defaults):** `100 + level^1.6 x 12`.
- Sword and axe also get a small damage bonus against mobs (default +0.4% per level).
- **16 ranks, one for every 5 levels:** Novice, Apprentice, Trainee, Journeyman, Skilled, Experienced, Expert, Veteran, Elite, Master, Grandmaster, Champion, Hero, Legend, Mythic, Immortal.

**Abilities (default values, all configurable):**

| Skill | Ability | Unlocks at | Effect |
|---|---|---|---|
| Sword | Vampiric Strike | Lv.20 | 15% chance to heal 1 heart when you hit a mob |
| Sword | Executioner | Lv.50 | +30% damage to mobs below 30% health |
| Axe | Cleave | Lv.20 | 25% chance to hit nearby hostile mobs for 50% damage |
| Axe | Lumberjack | Lv.40 | 20% chance for double logs |
| Pickaxe | Efficient Miner | Lv.20 | Mining gives a short Haste boost |
| Pickaxe | Double Drop | Lv.40 | 20% chance for double drops from ores and stone |
| Pickaxe | Jackpot | Lv.70 | 5% chance for triple drops |
| Shovel | Fast Digger | Lv.20 | Digging gives a short Haste boost |
| Shovel | Treasure Hunter | Lv.40 | 3% chance to dig up a random treasure |
| Shovel | Rich Soil | Lv.60 | 15% chance for double drops |

### Anti-lag system
Everything runs on slow timers. Nothing runs every tick, and the plugin never calls `System.gc()`.

- **Chunk trimming (every 30 s by default):** removes the oldest excess mobs per chunk. Defaults: 16 monsters, 20 animals and 10 other mobs per chunk. Tamed, leashed, name-tagged and ridden mobs are never removed. Villagers, golems and bosses are never touched.
- **Ground item clearing (every 5 minutes by default):** warns players at 30, 10, 5, 3, 2 and 1 seconds before. Named items and a keep-list (elytra, totems, netherite ingots, shulker boxes) are never removed.
- **Spawn limiter:** stops natural spawns in chunks that already hold more than 70 entities.
- **Lag mode:** the plugin estimates TPS. If it stays below 16, natural spawns pause and an emergency cleanup runs. When TPS recovers above 18.5, lag mode ends. Admins get a message in both cases.
- **Memory watch:** if heap usage stays above 92% for two checks in a row, an emergency cleanup runs.

The plugin cannot replace good server settings. On hosts like Aternos, lowering `view-distance` and `simulation-distance` helps a lot.

### Languages
- English is the default. Turkish is included.
- The server default is the `language` option in `config.yml`.
- Every player can change their own language with `/rpg lang en` or `/rpg lang tr`. The choice is saved.
- You can edit the texts in `plugins/MobMastery/lang/`. Missing keys fall back to the built-in text.

### Commands and permissions

| Command | Description | Permission |
|---|---|---|
| `/skills` (aliases `/skill`, `/beceri`) | Show your skills, ranks and abilities | `mobmastery.use` |
| `/rpg lang <en\|tr>` | Change your language | `mobmastery.use` |
| `/rpg help` | Show the command list | `mobmastery.use` |
| `/rpg status` | Show TPS estimate, lag mode and memory | `mobmastery.admin` |
| `/rpg cleanup` | Clean ground items and excess mobs now | `mobmastery.admin` |
| `/rpg reload` | Reload config and language files | `mobmastery.admin` |
| `/rpg setlevel <player> <sword\|axe\|pickaxe\|shovel> <level>` | Set a skill level | `mobmastery.admin` |

`mobmastery.use` is given to everyone by default. `mobmastery.admin` is given to operators.

### Installation
1. Download `MobMastery.jar`.
2. Put it in your server's `plugins` folder.
3. Start the server.
4. Optional: edit `plugins/MobMastery/config.yml`, then run `/rpg reload`.

**Requirements:** a Spigot or Paper server (built against API 1.21.10) and Java 21 (what Minecraft 1.21 already requires).

> **Aternos note:** Aternos does not allow uploading custom plugin jars, so this plugin only works there if Aternos adds it to their plugin list.

### Configuration
All settings are in `plugins/MobMastery/config.yml` and are commented. The main sections are:
- `language`: the server default language.
- `mob-levels`: spawn chance, level formula, strength scaling, name format, excluded mobs and worlds, and the four drop tiers. Drop lines use the format `MATERIAL:min:max:chance`.
- `skills`: XP values, damage bonus, ability chances and the shovel treasure list.
- `anti-lag`: chunk limits, item clearing, spawn limiter, TPS and memory thresholds.

Player data is stored in `plugins/MobMastery/data/` (one small file per player) and is saved on logout, every 5 minutes and on shutdown.

### Building from source
You need JDK 17 or newer and Maven.

```
mvn package
```

The jar will be at `target/MobMastery.jar`. This repository also includes a GitHub Actions workflow that builds the jar automatically on every push.

### Notes
- This is a first release. Bug reports and suggestions are welcome through the Issues tab.
- All code and the logo are original work. The plugin does not use any Minecraft assets.

---

## Türkçe

### İçindekiler
- [Özellikler](#özellikler)
- [Seviyeli mob'lar nasıl çalışır](#seviyeli-moblar-nasıl-çalışır)
- [Alet becerileri nasıl çalışır](#alet-becerileri-nasıl-çalışır)
- [Anti-lag sistemi](#anti-lag-sistemi)
- [Diller](#diller)
- [Komutlar ve yetkiler](#komutlar-ve-yetkiler)
- [Kurulum](#kurulum)
- [Ayarlar](#ayarlar)
- [Kaynak koddan derleme](#kaynak-koddan-derleme)
- [Notlar](#notlar)

**MobMastery:** Seviyeli mob'lar, yetenekli alet becerileri ve yerleşik anti-lag sistemi, hepsi tek ve hafif bir Spigot/Paper plugin'inde.

Spigot API **1.21.10** ile derlenmiştir. Bu ilk sürümdür, bir hata bulursan lütfen bildir.

### Özellikler
- **Seviyeli mob'lar (seviye 1-60).** Sadece bazı düşman mob'lar seviyeli doğar, diğerleri normal kalır. Seviye yükseldikçe mob'lar güçlenir ve daha iyi ganimet verir.
- **Alet becerileri (seviye 1-80).** Kılıç, balta, kazma ve kürek'in her birinin kendi seviyesi, XP çubuğu, rütbe adı ve açılabilir yetenekleri vardır.
- Seviye atlayınca veya yeni yetenek açılınca **ekranda yazı** çıkar.
- **Anti-lag.** Sadece yavaş zamanlayıcılar çalışır, her tick çalışan işlem yoktur. Kalabalık chunk'ları temizler, yerdeki eski eşyaları geri sayımla siler, sunucu zorlanınca doğal mob doğumunu durdurur ve yüksek bellek kullanımına tepki verir.
- **İki dil.** İngilizce (varsayılan) ve Türkçe. Her oyuncu kendi dilini seçer.
- **Her şey ayarlanabilir**, ayarlar `config.yml` dosyasındadır.

### Seviyeli mob'lar nasıl çalışır
- Düşman mob'lar (zombi, iskelet, örümcek ve benzerleri) belli bir şansla seviyeli doğar. Varsayılan: %50.
- **Seviye formülü (varsayılan):** `1 + (dünya doğuş noktasına uzaklık / 100)` ve -2 ile +4 arasında küçük bir rastgelelik. Nether +15, End +30 ekler. Sonuç 1-60 ile sınırlıdır.
- Seviyeli mob'un adında seviyesi görünür, örneğin `[Lv.23] Zombie`.
- **Güç seviyeyle artar (varsayılan):** her seviye yaklaşık %10 etkili can (mob daha az hasar alır) ve oyunculara yaklaşık %2,5 ekstra hasar ekler.
- **Ganimet dört kademelidir:** 1-15, 16-30, 31-45 ve 46-60. Yüksek kademe daha iyi eşya ve daha çok deneyim verir.
- Her kademede küçük bir şansla **nadir eşyalar** da düşer: elmas, ölümsüzlük totemi, netherite hurdası, büyülü altın elma ve en üst kademede netherite külçe. Nadir eşya düşünce mesaj alırsın.
- Varsayılan olarak ekstra ganimet sadece mob'u bir **oyuncu** öldürünce düşer.
- Boss mob'lar asla seviyelenmez. Başka mob türlerini veya dünyaları ayarlardan hariç tutabilirsin.

### Alet becerileri nasıl çalışır
Dört becerinin her birinin kendi seviyesi vardır (1-80).

| Beceri | XP nereden gelir |
|---|---|
| Kılıç | Elinde kılıç varken mob öldürmek |
| Balta | Mob öldürmek ve kütük kesmek |
| Kazma | Maden ve taş kazmak |
| Kürek | Toprak, kum, çakıl, kil ve benzeri blokları kazmak |

- Yüksek seviyeli mob'lar daha çok XP verir. Oyuncuların koyduğu bloklar XP vermez, böylece koy-kır ile XP kasılamaz.
- **Sonraki seviye için gereken XP (varsayılan):** `100 + seviye^1,6 x 12`.
- Kılıç ve balta ayrıca mob'lara karşı küçük bir hasar bonusu kazanır (varsayılan: seviye başına %0,4).
- **16 rütbe, her 5 seviyede bir:** Acemi, Çırak, Stajyer, Kalfa, Yetenekli, Deneyimli, Uzman, Kıdemli, Seçkin, Usta, Büyük Usta, Şampiyon, Kahraman, Efsane, Destansı, Ölümsüz.

**Yetenekler (varsayılan değerler, hepsi ayarlanabilir):**

| Beceri | Yetenek | Açıldığı seviye | Etki |
|---|---|---|---|
| Kılıç | Vampir Vuruşu | Sv.20 | Moba vurunca %15 şansla 1 kalp can yeniler |
| Kılıç | Cellat | Sv.50 | Canı %30'un altındaki mob'lara +%30 hasar |
| Balta | Savurma | Sv.20 | %25 şansla yakındaki düşman mob'lara %50 hasar verir |
| Balta | Oduncu | Sv.40 | %20 şansla çift kütük |
| Kazma | Verimli Madenci | Sv.20 | Kazarken kısa süreli Hız (Haste) kazanırsın |
| Kazma | Çift Ganimet | Sv.40 | Maden ve taştan %20 şansla çift eşya |
| Kazma | Jackpot | Sv.70 | %5 şansla üçlü eşya |
| Kürek | Hızlı Kazıcı | Sv.20 | Kazarken kısa süreli Hız (Haste) kazanırsın |
| Kürek | Hazine Avcısı | Sv.40 | %3 şansla rastgele bir hazine çıkarır |
| Kürek | Bereketli Toprak | Sv.60 | %15 şansla çift eşya |

### Anti-lag sistemi
Her şey yavaş zamanlayıcılarla çalışır. Hiçbir şey her tick çalışmaz ve plugin asla `System.gc()` çağırmaz.

- **Chunk temizliği (varsayılan her 30 sn):** her chunk'taki en eski fazla mob'ları siler. Varsayılanlar: chunk başına 16 düşman, 20 hayvan ve 10 diğer mob. Evcil, bağlı, isim etiketli ve bineğe binmiş mob'lara dokunulmaz. Köylüler, golemler ve boss'lar hiç silinmez.
- **Yerdeki eşya temizliği (varsayılan her 5 dakika):** temizlikten 30, 10, 5, 3, 2 ve 1 saniye önce oyuncuları uyarır. İsimli eşyalar ve koruma listesi (elytra, totem, netherite külçe, shulker kutusu) silinmez.
- **Doğum sınırlayıcı:** 70'ten fazla varlık bulunan chunk'larda doğal doğumu durdurur.
- **Lag modu:** plugin TPS'i tahmin eder. 16'nın altında kalırsa doğal doğum durur ve acil temizlik çalışır. TPS 18,5'in üstüne çıkınca lag modu biter. Yetkililer iki durumda da mesaj alır.
- **Bellek takibi:** bellek kullanımı üst üste iki ölçümde %92'nin üstünde kalırsa acil temizlik çalışır.

Plugin iyi sunucu ayarlarının yerini tutamaz. Aternos gibi sunucularda `view-distance` ve `simulation-distance` değerlerini düşürmek çok yardımcı olur.

### Diller
- Varsayılan dil İngilizcedir. Türkçe dahildir.
- Sunucu varsayılanı `config.yml` içindeki `language` ayarıdır.
- Her oyuncu kendi dilini `/rpg lang en` veya `/rpg lang tr` ile değiştirebilir. Seçim kaydedilir.
- Metinleri `plugins/MobMastery/lang/` klasöründen düzenleyebilirsin. Eksik satırlar yerleşik metinle tamamlanır.

### Komutlar ve yetkiler

| Komut | Açıklama | Yetki |
|---|---|---|
| `/skills` (takma adlar `/skill`, `/beceri`) | Becerilerini, rütbelerini ve yeteneklerini göster | `mobmastery.use` |
| `/rpg lang <en\|tr>` | Dilini değiştir | `mobmastery.use` |
| `/rpg help` | Komut listesini göster | `mobmastery.use` |
| `/rpg status` | Tahmini TPS, lag modu ve bellek durumunu göster | `mobmastery.admin` |
| `/rpg cleanup` | Yerdeki eşyaları ve fazla mob'ları şimdi temizle | `mobmastery.admin` |
| `/rpg reload` | Ayarları ve dil dosyalarını yeniden yükle | `mobmastery.admin` |
| `/rpg setlevel <oyuncu> <sword\|axe\|pickaxe\|shovel> <seviye>` | Beceri seviyesi ayarla | `mobmastery.admin` |

`mobmastery.use` varsayılan olarak herkese verilir. `mobmastery.admin` operatörlere verilir.

### Kurulum
1. `MobMastery.jar` dosyasını indir.
2. Sunucunun `plugins` klasörüne koy.
3. Sunucuyu başlat.
4. İsteğe bağlı: `plugins/MobMastery/config.yml` dosyasını düzenle, sonra `/rpg reload` yaz.

**Gereksinimler:** Spigot veya Paper sunucu (API 1.21.10 ile derlendi) ve Java 21 (Minecraft 1.21'in zaten istediği sürüm).

> **Aternos notu:** Aternos, kendi plugin jar dosyalarının yüklenmesine izin vermez. Bu plugin orada sadece Aternos kendi plugin listesine eklerse çalışır.

### Ayarlar
Bütün ayarlar `plugins/MobMastery/config.yml` içindedir ve açıklamalıdır. Ana bölümler:
- `language`: sunucu varsayılan dili.
- `mob-levels`: doğma şansı, seviye formülü, güç ölçekleme, isim biçimi, hariç tutulan mob ve dünyalar ve dört ganimet kademesi. Ganimet satırları `MATERIAL:min:max:chance` biçimindedir.
- `skills`: XP değerleri, hasar bonusu, yetenek şansları ve kürek hazine listesi.
- `anti-lag`: chunk sınırları, eşya temizliği, doğum sınırlayıcı, TPS ve bellek eşikleri.

Oyuncu verileri `plugins/MobMastery/data/` klasöründe tutulur (oyuncu başına küçük bir dosya). Çıkışta, her 5 dakikada bir ve sunucu kapanırken kaydedilir.

### Kaynak koddan derleme
JDK 17 veya üstü ve Maven gerekir.

```
mvn package
```

Jar `target/MobMastery.jar` yolunda oluşur. Bu depo, her push işleminde jar'ı otomatik derleyen bir GitHub Actions iş akışı da içerir.

### Notlar
- Bu ilk sürümdür. Hata bildirimleri ve öneriler için Issues sekmesini kullanabilirsin.
- Tüm kod ve logo özgün çalışmadır. Plugin hiçbir Minecraft görselini kullanmaz.
