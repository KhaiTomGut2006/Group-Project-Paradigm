# HANDOFF — Last Camp Survivor (EGCO 213 Group Project 3)

เอกสารส่งต่องานสำหรับ agent/คนถัดไป เขียนเมื่อ 2026-11-13 (อัปเดตล่าสุด: UI ทั้งหมดเปลี่ยนมาใช้ HollowCreek UI Kit แล้ว ดูหัวข้อ 10 ซึ่งแทนที่หัวข้อ 9)
อ่านคู่กับ `PROJECT_STRUCTURE.md` (ดีไซน์เกม), `Project_213_T3.pdf` (โจทย์และเกณฑ์คะแนน)
และ `dev-tools/ENGINEERING_SPEC.md` (ข้อตกลงทางวิศวกรรมและสัญญา API ระหว่างคลาส)

---

## 1. สถานะโดยสรุป

เกมเขียนครบทั้ง 12 คลาส compile ผ่าน และเล่นได้ครบ loop ในการทดสอบอัตโนมัติ
สิ่งที่ **ยังไม่ได้ทำ** คือการเล่นจริงด้วยคนพร้อมเมาส์/คีย์บอร์ด การฟังเสียง การรันบน Windows
และการใส่ชื่อ-รหัสสมาชิกจริง (รายละเอียดในหัวข้อ 6)

| หัวข้อ | สถานะ |
| :--- | :--- |
| Compile `javac -Xlint:all` บน JDK 26.0.2 ทั้ง 13 ไฟล์ | ผ่าน 0 error 0 warning (ตรวจซ้ำหลังแก้ UI kit แล้ว) |
| UI (HollowCreek UI Kit) ทั้ง launcher, HUD, Workshop, dialog, overlay | **เสร็จแล้ว** — render ครบ 16 ภาพ + ภาพ gameplay, ตรวจด้วยตาและแก้บั๊กเลย์เอาต์/9-slice ครบแล้ว (หัวข้อ 10) |
| ทุกไฟล์ `.java` เป็น ASCII ล้วน และมี header สมาชิก 4 คนด้านบน | ผ่าน (ใส่ชื่อ-รหัสจริงครบแล้ว) |
| Headless simulation ผ่าน `GamePanel` จริง 15 รอบ (5 ด่าน x 3 แบบ) | ไม่มี exception, ชนะ/แพ้ถูกรายงานครั้งเดียว, ชนะได้ที่ 300 วินาที |
| หน้าต่างจริง `GameFrame` ~9.5 วินาที | ~62 update/s, ปิดแล้วไม่มี thread ค้าง, เพลงหยุด |
| กด B เปิด `UpgradeDialog` จริงจากในเกม | ผ่าน (เกม pause ตอนเปิด, resume ตอนปิด, focus กลับ) |
| Launcher: ทุก control, validation ชื่อ, เปิดเกม, กลับมาหน้าแรก | ผ่านใน harness |
| เล่นจริงด้วยมือ / ฟังเสียง / Windows | **ยังไม่ได้ทำ** |
| Git | ยังไม่ได้ commit งานนี้เลย (ดูหัวข้อ 7) |

---

## 2. วิธี build และรัน

เครื่องนี้ **ไม่มี `java`/`javac` ใน PATH** ใช้ JDK 26 ที่ติดมากับ NetBeans:

```bash
ROOT="/Volumes/M.2/GitHub/Group Project Paradigm"
JDK="/Applications/Apache NetBeans.app/Contents/Home/bin"

# compile (ต้องไม่มี output เลย)
"$JDK/javac" -Xlint:all -d "$ROOT/target/classes" "$ROOT"/src/main/java/Project3_6813107/*.java

# run (ต้องใส่ src/main/java ใน classpath เพื่อให้หา resources เจอ)
cd "$ROOT" && "$JDK/java" -cp target/classes:src/main/java Project3_6813107.MainApplication
```

- เปิดใน NetBeans ได้ผ่าน `pom.xml` (release 26, มี `<resources>` ที่ copy ไฟล์ที่ไม่ใช่ `.java` จาก `src/main/java`)
- ไม่มี maven, brew, python PIL บนเครื่อง (python3 stdlib ใช้ได้)
- `AssetLoader` หา resource ตามลำดับ: classpath `/Project3_6813107/resources/` → ไฟล์ `src/main/java/Project3_6813107/resources/` → ไฟล์ `Project3_6813107/resources/` (relative กับ working dir)
- ไฟล์คะแนน `highscores.txt` ถูกเขียนตอนเกมจบ (ตอนนี้ยังไม่มีไฟล์นี้ในโปรเจค และถูกใส่ใน `.gitignore`)

---

## 3. โครงสร้างโปรเจคทั้งหมด

```text
Group Project Paradigm/
├── HANDOFF.md                      <-- ไฟล์นี้
├── PROJECT_STRUCTURE.md            <-- ดีไซน์เกมต้นฉบับ (ห้ามแก้โดยไม่ถามเจ้าของ)
├── Project_213_T3.pdf              <-- โจทย์ + rubric
├── pom.xml                         <-- Maven ขั้นต่ำสำหรับ NetBeans (Java 26)
├── .gitignore                      <-- ._*, .DS_Store, target/, highscores.txt
├── UPDATE 2_ZombiePack/            <-- ชุดภาพต้นฉบับ (read-only, ไม่ถูกอ้างตอน runtime, ไม่ต้องส่ง)
├── HollowCreek_UI_Kit_Free/        <-- ชุด UI pixel art ที่ถูกเพิ่มเข้ามาตอน 18:16 (27 MB, untracked) ยังไม่ได้ใช้ในโค้ด ดูหัวข้อ 6
├── dev-tools/                      <-- เครื่องมือทดสอบ (ไม่ใช่ส่วนของงานที่ส่ง)
│   ├── ENGINEERING_SPEC.md         <-- สัญญา API + ข้อบังคับ + การตัดสินใจด้าน design
│   ├── asset-tools/                <-- Zoom/Blobs/FrameBox ฯลฯ (ดู pixel ของ sprite sheet), gen_sounds.py (สร้าง .wav)
│   ├── phase1-harness/             <-- MapRender, SpriteSheet, Sim (ทดสอบ model layer)
│   ├── phase2b-harness/            <-- HeadlessSim (ยังใช้ได้กับโค้ดปัจจุบัน), RealWindow
│   ├── phase2c-harness/            <-- UiHarness ของ UI ชุดเก่า (ล้าสมัยแล้ว: อ้างถึง component ที่ถูกลบ)
│   └── ui-harness/                 <-- UiShots/UiShots2 + run.sh: render ทุกหน้าจอของ UI ใหม่เป็น PNG
└── src/main/java/Project3_6813107/ <-- โฟลเดอร์ที่ต้อง zip ส่ง (Project3_6813107.zip)
    ├── readme.txt                  <-- ชื่อ-รหัสสมาชิก 4 คน + คำอธิบาย
    ├── highscores.txt              <-- คะแนนจากการเล่นจริงของเจ้าของ (ถูก .gitignore, อย่าลบโดยไม่ถาม)
    ├── Theme.java            1620  <-- ระบบดีไซน์: สี ฟอนต์ ปุ่ม สวิตช์ chip slider combo ตาราง dialog (ใช้ร่วมทุกหน้าต่าง)
    ├── MainApplication.java   931  <-- main + launcher 900x620 (ภาพแคมป์จริงด้านซ้าย + 4 แท็บด้านขวา)
    ├── GameFrame.java         495  <-- หน้าต่างเกม 1100x660, HUD บน/ล่างแบบบาง
    ├── GamePanel.java        1364  <-- game loop thread, wave spawner thread, input, กล้อง, วาดฉาก + overlay
    ├── UpgradeDialog.java     598  <-- JDialog คราฟต์/อัปเกรด 860x660 (JTable + การ์ดรายละเอียด + กระเป๋า)
    ├── Entity.java            251  <-- superclass (x,y,hp,speed,hitbox)
    ├── Player.java            654  <-- inventory, อาวุธ, คราฟต์, ยิง
    ├── Monster.java           361  <-- AI 5 ชนิด, drop table
    ├── Bullet.java            335  <-- กระสุน/melee/ระเบิด/กระสุนศัตรู
    ├── DroppedItem.java       208  <-- ของดรอป, แม่เหล็กดูด, หีบ
    ├── GameData.java          605  <-- enum ทั้งหมด, สูตรคราฟต์ 12 สูตร, ค่า balance, คะแนน
    ├── AssetLoader.java       438  <-- โหลด/ตัด sprite, เสียง (crash-proof)
    ├── GameMap.java          1763  <-- แผนที่ 5 ด่าน, props, collision, flow field (ไฟล์ที่เพิ่มจากดีไซน์เดิม)
    └── resources/                  <-- 84 ไฟล์ รวม ~1.57 MB
        ├── characters/  (22)       <-- player, zombie1-4, bandit, npc (strip 32x32)
        ├── weapons/     (16)       <-- weapons_sheet, projectiles, *_shoot, *_flicker
        ├── items/       (12)       <-- ไอเทม 11 ชนิด (7 เฟรม 16x16) + chest_open
        ├── tiles/        (5)       <-- wasteland, interior, camp, flame_barrel, flag
        ├── ui/           (8)       <-- explosion, bullet_impact, popup, particles, icons, buttons, panel + kit/
        └── sounds/      (21)       <-- .wav 16-bit mono 22050 Hz ที่สังเคราะห์เอง รวม bgm 20 วินาที
```

ตัวเลขหลังชื่อไฟล์ `.java` คือจำนวนบรรทัด (รวม 9,623 บรรทัด)

### ความสัมพันธ์ระหว่างคลาส
- `MainApplication` → `new GameFrame(settings, this)` → GameFrame เรียก `launcher.onGameFinished()` ครั้งเดียวเมื่อจบ/ปิด
- `GameFrame` มี `GamePanel` อยู่ตรงกลาง และเปิด `new UpgradeDialog(this, player)` แล้ว `setVisible(true)` (constructor ของ dialog ต้องไม่ `setVisible` เอง)
- `GamePanel` ถือ `Player`, list ของ `Monster`/`Bullet`/`DroppedItem` (`CopyOnWriteArrayList`) และ `GameMap`
  **ต้องเรียก `player.setMap(map)`** ไม่งั้นผู้เล่นเดินทะลุกำแพง
- `Entity` implements `GameMap.Drawable` เพื่อให้ sort ความลึกร่วมกับ props ได้ (`GameMap.DEPTH_ORDER`)
- Thread: `gameThread` (16 ms ต่อ step), `waveSpawnerThread` (ตื่นทุก 2–3 วินาที เพิ่มแค่ตัวนับ `pendingSpawns`; game thread เป็นคนสร้างมอนสเตอร์จริง), `worldLock` กัน update กับ paint ชนกัน

---

## 4. จุดที่ต่างจาก PROJECT_STRUCTURE.md (ตั้งใจ)

1. เพิ่ม `GameMap.java` เป็นคลาสที่ 12 เพราะต้องการระบบแผนที่จริง และ `Theme.java` เป็นคลาสที่ 13 (ระบบดีไซน์ UI ที่ทุกหน้าต่างใช้ร่วมกัน)
2. คลาสย่อยเป็น nested class ไม่ใช่ไฟล์ใหม่: `GamePanel.Effect`, `GameMap.Prop`, `GameData.UpgradeRecipe`, `Theme.Card/Pill/FlatButtonUI` ฯลฯ
3. AI มอนสเตอร์ใช้ BFS flow field แทน `atan2` ตรง ๆ เพราะแบบเดิมติดสิ่งกีดขวางถาวร
4. Auto-Fire ยิงเฉพาะเมื่อมีมอนสเตอร์ในระยะที่ไม่ถูกกำแพงบัง (ถ้ายิงตามเมาส์ตลอดจะกินกระสุนหมดในไม่กี่วินาที)
5. อาวุธมี 8 ชนิด เลือกด้วยปุ่ม 1–8 หรือ mouse wheel; Axe เป็นอาวุธเริ่มต้นเท่านั้น ไม่มีสูตรคราฟต์
6. Pistol กระสุนไม่จำกัด อาวุธปืนอื่นใช้ไอเทม AMMO ร่วมกัน
7. Collision การเดินใช้กล่องเท้า (8 px ล่าง) เพื่อให้เดินหลังเต็นท์/รถได้; กระสุนถูกกั้นเฉพาะ footprint ของ prop
8. หีบบอสใช้แอนิเมชัน `chest_open` 32x16 ไม่ใช่ GreenChest/RedChest
9. ทุกคลาส Swing เป็น `final` + มี `serialVersionUID` เพื่อให้ `-Xlint:all` เงียบ
10. Look and feel: `Theme.installLookAndFeel()` ตั้ง cross-platform Metal + dark theme แล้วแต่ละ component ใช้ UI class ของตัวเองจาก `Theme` เพื่อให้หน้าตาเหมือนกันบน macOS และ Windows
11. UI ใหม่ตัดของที่ไม่จำเป็นออก: crafting log ใน Workshop (แทนด้วยข้อความผลลัพธ์บนการ์ด), checkbox Show Hitbox บน HUD (ยังตั้งได้ในแท็บ Settings), คอลัมน์ Class และ Kills ในตารางคะแนน, ภาพย่อแผนที่ (ภาพแคมป์ด้านซ้ายเปลี่ยนตามแผนที่ที่เลือกแทน), JOptionPane (แทนด้วย `Theme.ask`)

---

## 5. Event handler ที่ต้องนำเสนอ (ครบตามโจทย์)

| Method | Component | ทำอะไร |
| :--- | :--- | :--- |
| `GamePanel.keyPressed` | GamePanel | WASD เดิน, 1–8 เปลี่ยนอาวุธ, G ระเบิด, E ยา, B workshop, P/Esc pause |
| `GamePanel.mousePressed` | GamePanel | คลิกซ้ายเริ่มยิง, คลิกขวาปาระเบิด |
| `GamePanel.mouseMoved` (+`mouseDragged`) | GamePanel | เก็บตำแหน่งเมาส์เพื่อเล็งและวาด crosshair |
| `GamePanel.mouseWheelMoved` | GamePanel | วนเปลี่ยนอาวุธ |
| `MainApplication.actionPerformed` | ปุ่ม Start/Quit, แท็บ 4 อัน (`JToggleButton`), class chip (`JRadioButton` x5), combo, สวิตช์ (`JCheckBox` x5), ช่องชื่อ, `animationTimer` | สร้าง `GameSettings`, เปิดเกม, สลับแท็บ, อัปเดตภาพแคมป์ |
| `MainApplication.stateChanged` | `difficultySlider`, `volumeSlider` | แสดงผลจริงของระดับความยาก, ปรับเสียงสด |
| `GameFrame.actionPerformed` | ปุ่ม Workshop/Pause/Quit, สวิตช์ Auto-fire | เปิด dialog, pause, ปรับโหมดสด |
| `UpgradeDialog.actionPerformed` / `valueChanged` / `mouseClicked` | ปุ่ม Craft/Close, `recipeTable` | คราฟต์, อัปเดตการ์ดรายละเอียด, ดับเบิลคลิกเพื่อคราฟต์ |

Component ตามโจทย์: `JTextField` (ชื่อ), `JTextArea` (คำอธิบาย/วิธีเล่น), `JRadioButton` x5 (อาชีพ), `JCheckBox` x5 (สวิตช์ในแท็บ Settings), `JComboBox` (อาวุธ 6, แผนที่ 5), ปุ่ม Start เปิด `GameFrame`, รายชื่อสมาชิกมุมล่างซ้ายของ launcher
Extra component: `JTable` (สูตรคราฟต์, high score), `JSlider`, `JProgressBar` (HP, wave), `JToggleButton`

---

## 6. งานที่เหลือ (เรียงตามความสำคัญ)

1. **เล่นจริงด้วยคน** ทั้ง 5 ด่าน: การทดสอบทั้งหมดใช้ input สังเคราะห์ ยังไม่มีใครจับเมาส์เล่นจริง
   ต้องดู feel ของการเล็ง, crosshair แทน cursor, และ balance (ผู้เล่นที่กดมั่วตายที่ ~35 วินาทีใน difficulty 3; ยืนเฉยตายที่ ~22–26 วินาทีใน difficulty 5 + Double Spawn)
2. **ฟังเสียง** ทั้ง 21 ไฟล์: ตรวจได้แค่ว่าโหลดและเล่นได้ ยังไม่มีใครฟังว่าไพเราะหรือดังเกินไปไหม ถ้าต้องแก้ใช้ `dev-tools/asset-tools/gen_sounds.py`
3. **ใส่ข้อมูลกลุ่มจริง**: แก้แล้ว — เปลี่ยนแพ็กเกจเป็น `Project3_6813107` พร้อมใส่ชื่อ-รหัสสมาชิกจริง 4 คน (`6813105 Tapatsawin Chaieua`, `6813106 Thanapon Thanadunpremdet`, `6813107 Tanawat Wongwad`, `6813241 Natthanon Achachan`) ใน header ทุกไฟล์ `.java`, `GameData.GROUP_MEMBERS`, `readme.txt`, `AssetLoader.java`, `pom.xml` และ `dev-tools/` แล้ว
4. **รันบน Windows จริง** ด้วย Java 26: ยังทดสอบแค่การย่อหน้าต่างเลียนแบบขอบของ Windows
5. **วัด FPS ในหน้าต่างจริงตอนมีมอนสเตอร์ 120 ตัว**: ตัวเลข 120 ตัวมีแต่แบบ headless (update เฉลี่ย 0.08 ms, paint เฉลี่ย 3.4 ms)
6. **tick ช้าเป็นครั้งคราว**: headless พบ 1–2 step จาก 18,750 ที่ใช้ 20–34 ms (เกณฑ์คือ <16 ms) ครั้งแรกเกิดบนด่าน Survivor Camp
   แต่ orchestrator รันซ้ำแล้วด่านนั้นสูงสุดแค่ 1.15 ms และไปเกิดครั้งเดียว (29 ms) บน Abandoned Bunker แทน จึงไม่ผูกกับด่านใดด่านหนึ่ง
   น่าจะเป็น pause ของเครื่อง/JVM ยังไม่ได้ยืนยันบนเครื่องว่าง (ค่าเฉลี่ย 0.05 ms, p99 0.12 ms)
7. **ความยาวไฟล์สำหรับการนำเสนอ**: `GameMap.java` 1,764 และ `MainApplication.java` 1,465 บรรทัด ยาวสำหรับอธิบายหน้าห้อง ถ้าจะลดให้เริ่มจาก layout ของด่านใน GameMap และคลาส Theme ใน MainApplication
8. เล็กน้อย/ไม่บังคับ:
   - launcher ไม่แสดงผลของ difficulty ต่อคะแนน เพราะ `GameData` ไม่มีฟังก์ชัน score multiplier (สูตรอยู่ใน GamePanel)
   - ตอนตาย ตัวผู้เล่นถูกฝูงซอมบี้บัง มองไม่เห็นแอนิเมชันตาย
   - Zombie ธรรมดากับ Runner หน้าตาแทบเหมือนกัน ต่างกันแค่ความเร็ว
   - ขอบซ้าย/ขวาของด่านกลางแจ้งเป็นเส้นรั้วบาง ๆ
9. **สิ่งที่นักศึกษาต้องทำเอง**: อัดคลิป `demo_clip.mp4` (< 5 นาที) ใส่ใน `Project3_6813107/` และ zip ส่ง (ขนาด zip รวมต้องไม่เกิน 300 MB)
10. **UI ใหม่ที่ยังไม่ได้ตรวจ**: ฟอนต์บน Windows (`Theme` เลือก Segoe UI ก่อน, บน Mac ใช้ Helvetica Neue), การ hover/คลิกด้วยเมาส์จริง, dropdown ของ combo box ตอนเปิด, tooltip, dialog แบบไม่มีกรอบ (`Theme.ask`) บน Windows
11. **ช่องชื่อที่ผิด**: แก้แล้ว — ทั้งกรอบแดงของ `KitField` และข้อความ error ใต้ช่องจะหายทันทีเมื่อผู้ใช้พิมพ์/แก้ไขชื่อ (`DocumentListener` ใน `Theme.KitField` และ `MainApplication`)
12. **`HollowCreek_UI_Kit_Free/`**: เจ้าของโปรเจคเพิ่มเข้ามาระหว่างงาน UI ยังไม่ได้ถามว่าต้องการใช้หรือไม่ ชุดนี้เป็น pixel art ซึ่งคนละแนวกับ UI แบน ๆ ที่ทำไว้ ห้าม commit หรือใช้จนกว่าเจ้าของจะบอก และต้องอ่าน LICENSE.txt ของชุดนี้ก่อน
13. **`dev-tools/phase2c-harness`** ล้าสมัยแล้ว ใช้ `dev-tools/ui-harness/run.sh <โฟลเดอร์ผลลัพธ์>` แทน

---

## 7. ข้อควรระวัง

- **exFAT + macOS**: ทุกครั้งที่เขียนไฟล์ จะเกิดไฟล์ขยะ `._*` ข้าง ๆ ลบก่อน zip ส่งเสมอ:
  `find "src" -name '._*' -delete` (กฎ `<resources>` ใน `pom.xml` จะ copy ไฟล์พวกนี้เข้า `target/classes` ด้วยถ้าไม่ลบ)
- **Git**: repo ผูกกับ `origin` บน GitHub (`main` = `Initial commit` ซึ่งมีไฟล์ `._*` ติดไปด้วย)
  งานเกมทั้งหมดอยู่ใน commit `4d54fea` บน branch `feature/playable-game` ซึ่ง **ยังไม่ได้ push** (เครื่องนี้ไม่มี credential ของ GitHub ให้ agent ใช้; ให้เจ้าของ push เองผ่าน GitHub Desktop หรือ terminal)
  งานออกแบบ UI ใหม่ (หัวข้อ 9) **ยังไม่ถูก commit** ห้าม commit/push โดยเจ้าของไม่ได้สั่ง
- **ลิขสิทธิ์ชุดภาพ** (`UPDATE 2_ZombiePack/LICENSE.txt`): ใช้ในโปรเจคได้ แต่ห้ามแจกจ่ายตัว asset แยก และห้ามใช้กับ AI image generator
- **harness ใน `dev-tools/`** มี path ของ scratchpad เดิมฝังอยู่ในสคริปต์ `run.sh`/`hrun.sh` และอ้างถึง stub ที่ไม่จำเป็นแล้ว ต้องแก้ path ก่อนใช้
  (compile harness คู่กับ 12 ไฟล์จริงได้เลย; `HeadlessSim` และ `RealWindow` อยู่ใน package `Project3_6813107` เพื่อเข้าถึงสมาชิก package-private)
- **ภาพผลการทดสอบ** (แผนที่ 5 ด่าน, เฟรม gameplay, ภาพ launcher/dialog) อยู่ในโฟลเดอร์ชั่วคราวซึ่งอาจถูกลบ:
  `/private/tmp/claude-501/-Users-tanawatgut--claude-spares-spare-79cmOx/96a86cfa-b7a6-4475-ba1d-800397290095/scratchpad/` (`phase1/`, `phase2b/`, `phase2c/`) สร้างใหม่ได้จาก harness
- **ข้อบังคับที่ห้ามพัง** (รายละเอียดใน `dev-tools/ENGINEERING_SPEC.md` ข้อ 1): ใช้แค่ `javax.swing`/`java.awt`/`javax.sound.sampled`/`javax.imageio`, ไม่มี library ภายนอก, frame ไม่เกิน 1200x700, source เป็น ASCII, ไม่มี absolute path, ไม่มี warning ภายใต้ `-Xlint:all`

---

## 8. วิธีทำงานที่ใช้มา

- Orchestrator (Fable 5.2): เขียน spec/สัญญา API, ตรวจภาพและ compile ซ้ำด้วยตัวเอง, ตัดสินใจเรื่อง design
- Developer (Opus 5.5): เฟส 1 = resources + เสียง + model layer + GameMap; เฟส 2B = GamePanel + GameFrame; เฟส 2C = MainApplication + UpgradeDialog (2B และ 2C ทำขนานกันโดย compile กับ stub ของอีกฝ่าย)
- ทุกเฟสต้องมี harness ที่รันได้จริงและภาพ render ที่เปิดดูได้ก่อนส่งงาน

---

## 9. ระบบ UI ใหม่ (minimal / modern)

เจ้าของโปรเจคบอกว่า UI ชุดแรก "รก ดูโบราณ ใช้งานยากและซับซ้อน" จึงออกแบบใหม่ทั้งหมดโดยไม่แตะ logic ของเกม
(update loop, AI, คราฟต์, คะแนน ไม่เปลี่ยน; จำลองการเล่น 15 รอบหลังเปลี่ยนแล้วยังชนะได้ครบ 5 ด่าน ไม่มี exception)

### หลักการ
- **โทนมืด สีเน้นสีเดียว** (อำพัน `Theme.ACCENT` เหมือนกองไฟในแคมป์) สีเขียว/แดงใช้เฉพาะสถานะ (พอ/ไม่พอ, HP)
- **ลำดับชั้นของตัวอักษร** แทนกรอบ: หัวข้อเล็กตัวพิมพ์ใหญ่สีจาง (`Theme.caption`) อยู่เหนือเนื้อหา ไม่มีกล่องมีหัวข้อซ้อนกัน
- **ซ่อนของที่ไม่ใช้บ่อย**: launcher แบ่งเป็น 4 แท็บ, HUD แสดงเฉพาะไอเทมที่มี, ตาราง Workshop แสดงสถานะสั้น ๆ แล้วอธิบายเต็มในการ์ดด้านขวา
- **ทุกอย่างเป็น Swing ล้วน**: ยังเป็น `JButton`, `JCheckBox`, `JSlider` ฯลฯ เปลี่ยนแค่การวาดผ่าน UI class ใน `Theme` (อธิบายตอนนำเสนอได้ว่าเป็น pluggable look and feel)

### สิ่งที่อยู่ใน `Theme.java`
| ส่วน | ใช้ทำอะไร |
| :--- | :--- |
| สี `BG, SURFACE, SURFACE_2/3, LINE, TEXT, TEXT_2/3, ACCENT, GOOD, BAD, WARN` | ทุกหน้าต่างและ overlay ในเกม |
| `font()`, `captionFont()` | เลือกฟอนต์ตัวแรกที่มีในเครื่อง: Segoe UI → Helvetica Neue → Inter → Roboto → Arial → SansSerif |
| `button()` + `FlatButtonUI` (PRIMARY/SECONDARY/GHOST), `setKeyHint()` | ปุ่มแบนมุมโค้ง แสดงปุ่มลัด เช่น "Workshop [B]" |
| `styleSwitch()` + `SwitchIcon` | `JCheckBox` เป็นสวิตช์เปิด/ปิด |
| `styleChip()` + `ChipUI` | `JRadioButton` อาชีพ และ `JToggleButton` แท็บ |
| `textField()`, `styleSlider()`, `styleCombo()`, `scroll()`, `styleTable()` | ช่องกรอก, slider, combo, scroll bar บาง, ตารางไม่มีเส้นตาราง |
| `Card`, `Pill`, `RoundBorder` | พื้นหลังการ์ด, ป้ายเม็ดยา (ปุ่มลัด, ไอเทม) |
| `ask()` | dialog ยืนยัน/สรุปผลแทน `JOptionPane` (Quit, สรุปผลตอนจบเกม) |
| `fit()`, `scale()`, `resize()`, `smooth()`, `drawKeyCap()` | ขยายภาพ pixel art ให้คม, วาดปุ่มลัดใน overlay |

### หน้าจอ
- **Launcher**: ซ้าย = ภาพแคมป์จริงของแผนที่ที่เลือก (props เคลื่อนไหว + ตัวละครถืออาวุธที่เลือก) พร้อมชื่อเกมและรายชื่อสมาชิก; ขวา = แท็บ Play (ชื่อ, อาชีพ, อาวุธ, แผนที่, ความยาก), Settings (สวิตช์ 5 ตัว + ระดับเสียง), Scores (ตาราง top 10 หรือข้อความว่างเมื่อยังไม่มีคะแนน), How to play (เป้าหมาย, ปุ่มลัดแบบ key cap, วงจรเก็บของ-คราฟต์); ล่าง = Quit / Start survival
- **HUD ในเกม**: แถบบน = Health (bar เปลี่ยนสีเขียว/เหลือง/แดง), Armor, Wave + เวลาที่เหลือ + bar wave, Score, Kills, Pause [P], Quit; แถบล่าง = อาวุธ + กระสุน, ไอเทมที่มี (ซ่อนตัวที่เป็น 0), สวิตช์ Auto-fire, Workshop [B]
- **Overlay ในเกม** (`GamePanel`): การ์ด Pause พร้อมปุ่มลัด, หลอดเลือดบอส, มินิแมปมุมโค้ง, ข้อความแจ้งเตือนแบบเม็ดยา, ป้าย wave, หน้าจบเกม
- **Workshop**: ตาราง 3 คอลัมน์ (Upgrade / Cost / Status: Ready, Locked, Need parts, Lv x/y, Owned, MAX) + การ์ดรายละเอียด (ไอคอน, คำอธิบาย, have/need ทีละไอเทม, เหตุผลที่คราฟต์ไม่ได้, ปุ่ม Craft [Enter]) + กระเป๋า 11 ช่องที่ขึ้นกรอบเขียว/แดงตามสูตรที่เลือก

### วิธีตรวจ UI
```bash
dev-tools/ui-harness/run.sh /tmp/ui-shots
```
เปิดหน้าต่างจริงสั้น ๆ แล้วบันทึก PNG: launcher ทุกแท็บ, ชื่อผิด, ตารางคะแนนที่มีข้อมูล (คะแนนปลอมเขียนในโฟลเดอร์ชั่วคราว ไม่ใช่ในโปรเจค), Workshop 3 สถานะ, HUD ขณะเล่น, Pause, dialog สรุปผลและ Quit
และปิดเกมแล้วตรวจว่าไม่มี game thread ค้าง (ผลล่าสุด: 0)


---

## 10. UI ชุดปัจจุบัน: HollowCreek UI Kit (แทนที่ดีไซน์แบนในหัวข้อ 9)

เจ้าของโปรเจคสั่งให้ "นำ UI จาก HollowCreek_UI_Kit_Free มาใช้ทั้งหมด" จึงเขียน `Theme.java` ใหม่ทั้งไฟล์บน kit นี้
และเขียน `MainApplication`, `GameFrame`, `UpgradeDialog` ใหม่ + เปลี่ยน overlay ใน `GamePanel` (logic เกมไม่ได้แตะ)
หัวข้อ 9 ยังเก็บไว้เป็นประวัติ แต่ข้อมูลในตาราง `Theme` ของหัวข้อ 9 **ล้าสมัยแล้ว**

### สถานะล่าสุด (ตรวจครบแล้ว)
- Compile `javac -Xlint:all` บน JDK 26 ทั้ง 13 ไฟล์: **ผ่าน 0 error 0 warning** และทุกไฟล์เป็น pure ASCII (0x00..0x7F)
- `dev-tools/ui-harness/run.sh`: รันผ่านและตรวจด้วยตาครบทั้ง 16 ภาพ (`launcher_play/options/scores/help/bad_name/small/scores_filled`, `workshop_start/after_craft/locked`, `game_running/hurt/paused/over_screen`, `summary_dialog`, `quit_dialog`), ปิดเกมแล้ว `game threads alive after close: 0`
- `dev-tools/phase2b-harness/HeadlessSim`: รันผ่านครบ 15 รอบ (5 ด่าน x 3 แบบ) + ทดสอบ perf 120 มอนสเตอร์ 0 exception และตรวจภาพ gameplay overlay (ป้าย `"WAVE 1"`, หลอดเลือดบอส, minimap) แล้ว
- ยังไม่ได้ commit (รอคำสั่งเจ้าของโปรเจค)

### บั๊ก / จุดที่ตรวจพบและแก้แล้ว
1. **Launcher (`Theme.KitButtonUI`, `Theme.KitField`, `Theme.TextBlock`, `MainApplication`)**:
   - ลด padding แนวนอนของปุ่ม `JToggleButton`/`JRadioButton` ใน `KitButtonUI.getPreferredSize` จาก `14*s` เหลือ `8*s` และตั้ง `minimumSize` ให้ `KitField`, `styleCombo`, `styleSlider` + `setRows(2)` บน `weaponInfoArea`/`mapInfoArea` ป้องกัน `GridBagLayout` fallback ไป `MINSIZE` ที่บีบช่องกรอกชื่อและตัดคำอธิบายแผนที่/อาวุธ
   - เพิ่ม `Theme.TextBlock` (คำนวณความสูงเมื่อ `CardLayout` เพิ่งสลับมาแสดงแท็บ `HELP` และคง `preferredWidth` ขั้นต่ำที่ 100) แก้ปัญหาข้อความใน `GOAL` และ `LOOT AND CRAFT` ถูกตัดเหลือ 1 บรรทัด
   - เพิ่ม `DocumentListener` ใน `KitField` และ `MainApplication.clearNameError()` ให้กรอบแดงและข้อความ error หายทันทีที่เริ่มแก้ไขชื่อ
2. **Workshop (`UpgradeDialog.RecipeCellRenderer`)**: แยก border ของเซลล์ที่เลือกเป็น outer bottom `Theme.SLOT` และ inner left `Theme.GOLD` แทน `MatteBorder` เดียว เพื่อไม่ให้มีเส้นสีทองใต้คอลัมน์แรก
3. **HUD & Overlay (`GameFrame`, `GamePanel`)**:
   - `GameFrame.refreshHud()` ใช้ `Theme.fit(Theme.kit("bars/" + heart, 1), 27, 24)` ตัดขอบโปร่งใสรอบ sprite หัวใจและขยาย 3x ให้สมดุลกับแถบเลือด
   - `GamePanel.drawBanner()` จัด Y ของเลข wave ให้ตรงกับ `top` ของ sprite `titles/wave.png`
4. **9-Slice & Dialog Box (`Theme.Panel.slice()`, `Theme.SpeechBox`)**:
   - ลบจุดส้มส่วนเกินบนขอบขวาของ `METAL` (`panel_metal_48`) และ `SpeechBox` (`dialog_box_176x48`) ไม่ให้ tile ซ้ำลงมาตามขอบขวา
   - ลบรอยเทปตรงกลางบนขอบบนของ `PAPER` (`panel_paper_48`) และปรับ slice เป็น `16, 15, 16, 9` ให้เส้นบรรทัดห่างเท่ากัน 6 px ทุกช่วง
   - ปรับ slice ของ `DANGER` (`panel_danger_48`) เป็น `12, 12, 12, 12` ไม่ให้ลายแถบเฉียงเหลื่อมช่วงรอยต่อ
5. **หมายเหตุ**: ยังคงเหลือการตรวจบน Windows จริง และปรับความพร้อมปุ่มขนาดใหญ่ในสภาพแวดล้อม Windows (ดูหัวข้อ 6 ข้อ 4 และ 10)

### ไฟล์ที่เกี่ยวข้อง
- `resources/ui/kit/` = สำเนา kit ทั้งชุด (196 ไฟล์, ~37 KB จริง) รวม LICENSE/README และปุ่มที่สร้างเพิ่ม `keys/key_B, key_G, key_P, key_1to8` (สร้างด้วย `dev-tools/asset-tools/MakeKeys.java` จาก font ของ kit เอง)
- โฟลเดอร์ต้นฉบับ `HollowCreek_UI_Kit_Free/` ที่ root ยัง untracked (27 MB บน exFAT เพราะ cluster ใหญ่) ไม่จำเป็นต้อง commit เพราะใช้สำเนาใน resources
- License ของ kit: ใช้ในเกมได้ ห้ามขาย/แจก asset แยก ห้าม NFT ห้ามใช้เทรน AI

### สิ่งที่ `Theme.java` (kit) มีให้ใช้
| ส่วน | จาก kit |
| :--- | :--- |
| `kit(path, scale)` | โหลด/ขยายภาพจาก `resources/ui/kit/` แบบ nearest-neighbour + cache |
| `drawPixelText`, `PixelLabel`, `caption()` | font 3x5 ของ kit (ตัวพิมพ์ใหญ่ ตัวเลข สัญลักษณ์บางตัว) |
| `NineSlice`, `Panel {METAL, PAPER, GLASS, DANGER}`, `Card` | panel 9-slice |
| `button()`, `KitButtonUI` (RED/GREY), `styleToggle`, `setKeyHint`, `setButtonScale` | ปุ่ม 4 สถานะ, ปุ่มสำเร็จรูป START/QUIT/OPTIONS/CONTINUE/BACK, key sprite บนปุ่ม |
| `styleCheckBox` (`CheckIcon`), `styleSlider` (`KitSliderUI`) | checkbox_on/off, slider |
| `bar()` / `drawBar` + `Fill` | bar_frame + fill_health/stamina/ammo/infection/water |
| `KitField`, `styleCombo`, `slotFrame` | กรอบ inventory slot (ทอง = กำลังพิมพ์, แดง = ผิด) |
| `Slot`, `drawSlot`, `SlotKind` | inventory slot normal/selected/empty/locked |
| `SpeechBox` | dialog_box + portrait (พ่อค้าใน Workshop) |
| `drawBanner`, `drawAmmoCounter`, `drawHotbarFrame`, `drawMinimapFrame` | day banner, ammo counter, hotbar, minimap frame (ตัดส่วนข้อความตัวอย่างออก) |
| `cursor()`, `arrowCursor()`, `handCursor()` | cursor ของ kit (ปุ่มเป็นรูปมือ) |
| `ask(owner, panel, titleSprite, ...)` | dialog สรุปผล (titles/survived, titles/game_over) และ Quit (panel DANGER) |

### ส่วนของ kit ที่ใช้ในแต่ละหน้าจอ
- **Launcher**: ชื่อเกม font kit ขนาด 6x, แท็บ/อาชีพเป็นปุ่ม kit (เทา/แดงเมื่อเลือก), METAL panel, HELP เป็น PAPER พร้อม key sprite, ช่องชื่อ/combo เป็นกรอบ slot, checkbox/slider ของ kit, ปุ่ม QUIT และ START (3x) สำเร็จรูป, cursor ลูกศร/มือ
- **HUD**: METAL plate บน/ล่าง, heart full/half/empty, health bar, health_segments = ระดับเกราะ, status icon poisoned/injured/bleeding, stamina bar = เวลา wave, hotbar 8 ช่อง (ทอง = ถืออยู่, กุญแจ = ยังไม่ได้คราฟต์), ammo counter, backpack เป็น slot, ปุ่ม WORKSHOP + key B
- **ในเกม**: crosshair ของ kit ตามประเภทอาวุธ (melee = cursor_attack), ตัวเลข damage เป็น font kit, หลอดเลือดบอสบน GLASS + portrait_zombie, minimap frame + map markers (player/zombie boss/loot/safe = แคมป์), ป้าย wave = titles/wave + red banner, Pause = titles/paused + GLASS + key sprite, จบเกม = titles/you_died / survived
- **Workshop**: SpeechBox พ่อค้า (portrait_other), WORKSHOP font kit, ปุ่ม BACK สำเร็จรูป, ตารางบน METAL, รายละเอียดบน PAPER, ปุ่ม CRAFT + key Enter, backpack เป็น slot (ทอง = สูตรนี้ใช้, เลขแดง = ไม่พอ)
- **ไม่ได้ใช้** (ไม่มีกลไกในเกมรองรับ): status icon cold/hidden/hungry/thirsty/tired/noise/infected, preset bar (health_10 ฯลฯ ใช้วาด bar แบบ dynamic แทน), portrait_survivor, minimap_frame_round, inventory hotbar 6 ช่องแบบเต็ม (ใช้เฉพาะขอบ)

### งานต่อไปที่แนะนำ (เรียงลำดับ)
1. เล่นจริงด้วยมือ + ตรวจเสียงและ performance บน Windows
2. ค่อย commit เมื่อเจ้าของสั่ง

---

## 11. อัปเดตเพิ่มเติม (2026-11-14) — Scavenge, Base Building, Camp NPCs & Horde Raids

ปรับ loop เกมจากการกัน wave อย่างเดียวเป็น **ออกไปยิงซอมบี้/เปิด Supply Chest รอบแผนที่ → กลับมาที่ฐานคุยกับ NPC (`[B]`) เพื่อคราฟต์อาวุธ/อัปเกรด/รักษา/สร้างสิ่งปลูกสร้าง → ป้องกัน Horde Raid เป็นระยะ**:

1. **`GameData.java` & `Player.java`**:
   - เพิ่มสูตรคราฟต์/สร้างฐาน 5 รายการใน `RecipeKind` และ `RECIPES`:
     - `Build Guard Post` (`BASE_TURRET`, max Lv.4): ตั้งจุดยิง NPC Guard รอบฐาน ยิงซอมบี้ที่เข้าใกล้
     - `Fortify Base Walls` (`BASE_WALL`, max Lv.3): สร้างแนวป้องกันรอบค่าย และลดความเสียหายที่ได้รับในฐาน (-15%/lv)
     - `Build Medical Tent` (`BASE_MEDIC`, max Lv.3): ฟื้นเลือดอัตโนมัติและแก้พิษเมื่อยืนอยู่ในฐาน
     - `Build Supply Depot` (`BASE_FARM`, max Lv.3): แปลงผักและคลังกระสุนที่ฐาน ผลิต `Ammo` / `Food` / `Medkit` ให้เก็บเป็นระยะ
     - `Doctor Treatment` (`HEAL_NOW`): จ่าย `Food` + `Scraps` เพื่อรักษา +65 HP และแก้พิษทันทีในฐาน
2. **`GameMap.java` & `Monster.java`**:
   - `GameMap.applyBaseUpgrades(...)`: วางสิ่งก่อสร้างจริง (กำแพงลังไม้/ยาง/ถังน้ำมัน, NPC Guard, เต็นท์พยาบาล/เตียง, แปลงข้าวโพด/ข้าวสาลี/กล่องเสบียง) ใน/รอบ `campArea` พร้อมคำนวณ walkability และ minimap ใหม่
   - `Monster`: แยกระหว่าง `raiding` (มอนสเตอร์บุกฐานและบอส) กับมอนสเตอร์รอบนอกที่เดินลาดตระเวน (`wanderOuterZone`) ไม่เดินเข้าฐานช่วง Scavenge และจะไล่ตามเมื่อผู้เล่นเข้าไปในระยะตรวจจับหรือยิงโดน
3. **`GamePanel.java`, `GameFrame.java`, `UpgradeDialog.java`, `MainApplication.java`**:
   - สลับช่วง `SCAVENGE` และ `HORDE RAID` ภายในแต่ละรอบ 30 วินาที พร้อมเกิด Supply Chest รอบนอกให้ไปเก็บ
   - กด `B` / ปุ่ม `BASE NPC` ได้เมื่อเดินกลับมาในเขต `campArea` (นอกเขตจะขึ้นข้อความเตือนให้กลับฐาน)
   - `UpgradeDialog` เปลี่ยนบทพูด NPC ตามประเภท (Gunsmith / Engineer / Medic / Trainer) และปุ่มคำสั่ง (`CRAFT` / `BUILD` / `HEAL NOW`)
