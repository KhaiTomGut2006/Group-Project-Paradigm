# HANDOFF — Last Camp Survivor (EGCO 213 Group Project 3)

เอกสารส่งต่องานสำหรับ agent/คนถัดไป เขียนเมื่อ 2026-11-13
อ่านคู่กับ `PROJECT_STRUCTURE.md` (ดีไซน์เกม), `Project_213_T3.pdf` (โจทย์และเกณฑ์คะแนน)
และ `dev-tools/ENGINEERING_SPEC.md` (ข้อตกลงทางวิศวกรรมและสัญญา API ระหว่างคลาส)

---

## 1. สถานะโดยสรุป

เกมเขียนครบทั้ง 12 คลาส compile ผ่าน และเล่นได้ครบ loop ในการทดสอบอัตโนมัติ
สิ่งที่ **ยังไม่ได้ทำ** คือการเล่นจริงด้วยคนพร้อมเมาส์/คีย์บอร์ด การฟังเสียง การรันบน Windows
และการใส่ชื่อ-รหัสสมาชิกจริง (รายละเอียดในหัวข้อ 6)

| หัวข้อ | สถานะ |
| :--- | :--- |
| Compile `javac -Xlint:all` บน JDK 26.0.2 ทั้ง 12 ไฟล์ | ผ่าน 0 error 0 warning (ตรวจซ้ำโดย orchestrator) |
| ทุกไฟล์ `.java` เป็น ASCII ล้วน และมี header สมาชิก 5 บรรทัด | ผ่าน (ยังเป็น placeholder) |
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
"$JDK/javac" -Xlint:all -d "$ROOT/target/classes" "$ROOT"/src/main/java/Project3_XXX/*.java

# run (ต้องใส่ src/main/java ใน classpath เพื่อให้หา resources เจอ)
cd "$ROOT" && "$JDK/java" -cp target/classes:src/main/java Project3_XXX.MainApplication
```

- เปิดใน NetBeans ได้ผ่าน `pom.xml` (release 26, มี `<resources>` ที่ copy ไฟล์ที่ไม่ใช่ `.java` จาก `src/main/java`)
- ไม่มี maven, brew, python PIL บนเครื่อง (python3 stdlib ใช้ได้)
- `AssetLoader` หา resource ตามลำดับ: classpath `/Project3_XXX/resources/` → ไฟล์ `src/main/java/Project3_XXX/resources/` → ไฟล์ `Project3_XXX/resources/` (relative กับ working dir)
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
├── dev-tools/                      <-- เครื่องมือทดสอบ (ไม่ใช่ส่วนของงานที่ส่ง)
│   ├── ENGINEERING_SPEC.md         <-- สัญญา API + ข้อบังคับ + การตัดสินใจด้าน design
│   ├── asset-tools/                <-- Zoom/Blobs/FrameBox ฯลฯ (ดู pixel ของ sprite sheet), gen_sounds.py (สร้าง .wav)
│   ├── phase1-harness/             <-- MapRender, SpriteSheet, Sim (ทดสอบ model layer)
│   ├── phase2b-harness/            <-- HeadlessSim, RealWindow (ทดสอบ GamePanel/GameFrame)
│   └── phase2c-harness/            <-- UiHarness (ทดสอบ MainApplication/UpgradeDialog)
└── src/main/java/Project3_XXX/     <-- โฟลเดอร์ที่ต้อง zip ส่ง (Project3_XXX.zip)
    ├── readme.txt                  <-- ชื่อ-รหัสสมาชิก (placeholder)
    ├── MainApplication.java  1465  <-- main + launcher 900x620 + คลาส Theme/Section ที่ใช้ร่วมกัน
    ├── GameFrame.java         475  <-- หน้าต่างเกม 1100x660, HUD (JProgressBar, ปุ่ม, checkbox)
    ├── GamePanel.java        1337  <-- game loop thread, wave spawner thread, input, กล้อง, วาดฉาก
    ├── UpgradeDialog.java     625  <-- JDialog คราฟต์/อัปเกรด (JTable + log)
    ├── Entity.java            252  <-- superclass (x,y,hp,speed,hitbox)
    ├── Player.java            655  <-- inventory, อาวุธ, คราฟต์, ยิง
    ├── Monster.java           362  <-- AI 5 ชนิด, drop table
    ├── Bullet.java            336  <-- กระสุน/melee/ระเบิด/กระสุนศัตรู
    ├── DroppedItem.java       209  <-- ของดรอป, แม่เหล็กดูด, หีบ
    ├── GameData.java          607  <-- enum ทั้งหมด, สูตรคราฟต์ 12 สูตร, ค่า balance, คะแนน
    ├── AssetLoader.java       439  <-- โหลด/ตัด sprite, เสียง (crash-proof)
    ├── GameMap.java          1764  <-- แผนที่ 5 ด่าน, props, collision, flow field (ไฟล์ที่เพิ่มจากดีไซน์เดิม)
    └── resources/                  <-- 84 ไฟล์ รวม ~1.57 MB
        ├── characters/  (22)       <-- player, zombie1-4, bandit, npc (strip 32x32)
        ├── weapons/     (16)       <-- weapons_sheet, projectiles, *_shoot, *_flicker
        ├── items/       (12)       <-- ไอเทม 11 ชนิด (7 เฟรม 16x16) + chest_open
        ├── tiles/        (5)       <-- wasteland, interior, camp, flame_barrel, flag
        ├── ui/           (8)       <-- explosion, bullet_impact, popup, particles, icons, buttons, panel
        └── sounds/      (21)       <-- .wav 16-bit mono 22050 Hz ที่สังเคราะห์เอง รวม bgm 20 วินาที
```

ตัวเลขหลังชื่อไฟล์ `.java` คือจำนวนบรรทัด (รวม 8,526 บรรทัด)

### ความสัมพันธ์ระหว่างคลาส
- `MainApplication` → `new GameFrame(settings, this)` → GameFrame เรียก `launcher.onGameFinished()` ครั้งเดียวเมื่อจบ/ปิด
- `GameFrame` มี `GamePanel` อยู่ตรงกลาง และเปิด `new UpgradeDialog(this, player)` แล้ว `setVisible(true)` (constructor ของ dialog ต้องไม่ `setVisible` เอง)
- `GamePanel` ถือ `Player`, list ของ `Monster`/`Bullet`/`DroppedItem` (`CopyOnWriteArrayList`) และ `GameMap`
  **ต้องเรียก `player.setMap(map)`** ไม่งั้นผู้เล่นเดินทะลุกำแพง
- `Entity` implements `GameMap.Drawable` เพื่อให้ sort ความลึกร่วมกับ props ได้ (`GameMap.DEPTH_ORDER`)
- Thread: `gameThread` (16 ms ต่อ step), `waveSpawnerThread` (ตื่นทุก 2–3 วินาที เพิ่มแค่ตัวนับ `pendingSpawns`; game thread เป็นคนสร้างมอนสเตอร์จริง), `worldLock` กัน update กับ paint ชนกัน

---

## 4. จุดที่ต่างจาก PROJECT_STRUCTURE.md (ตั้งใจ)

1. เพิ่ม `GameMap.java` เป็นคลาสที่ 12 เพราะต้องการระบบแผนที่จริง
2. คลาสย่อยเป็น nested class ไม่ใช่ไฟล์ใหม่: `GamePanel.Effect`, `GameMap.Prop`, `GameData.UpgradeRecipe`, `MainApplication.Theme/Section` ฯลฯ
3. AI มอนสเตอร์ใช้ BFS flow field แทน `atan2` ตรง ๆ เพราะแบบเดิมติดสิ่งกีดขวางถาวร
4. Auto-Fire ยิงเฉพาะเมื่อมีมอนสเตอร์ในระยะที่ไม่ถูกกำแพงบัง (ถ้ายิงตามเมาส์ตลอดจะกินกระสุนหมดในไม่กี่วินาที)
5. อาวุธมี 8 ชนิด เลือกด้วยปุ่ม 1–8 หรือ mouse wheel; Axe เป็นอาวุธเริ่มต้นเท่านั้น ไม่มีสูตรคราฟต์
6. Pistol กระสุนไม่จำกัด อาวุธปืนอื่นใช้ไอเทม AMMO ร่วมกัน
7. Collision การเดินใช้กล่องเท้า (8 px ล่าง) เพื่อให้เดินหลังเต็นท์/รถได้; กระสุนถูกกั้นเฉพาะ footprint ของ prop
8. หีบบอสใช้แอนิเมชัน `chest_open` 32x16 ไม่ใช่ GreenChest/RedChest
9. ทุกคลาส Swing เป็น `final` + มี `serialVersionUID` เพื่อให้ `-Xlint:all` เงียบ
10. Look and feel: `main` ตั้ง cross-platform Metal + dark theme เพื่อให้หน้าตาเหมือนกันบน macOS และ Windows

---

## 5. Event handler ที่ต้องนำเสนอ (ครบตามโจทย์)

| Method | Component | ทำอะไร |
| :--- | :--- | :--- |
| `GamePanel.keyPressed` | GamePanel | WASD เดิน, 1–8 เปลี่ยนอาวุธ, G ระเบิด, E ยา, B workshop, P/Esc pause |
| `GamePanel.mousePressed` | GamePanel | คลิกซ้ายเริ่มยิง, คลิกขวาปาระเบิด |
| `GamePanel.mouseMoved` (+`mouseDragged`) | GamePanel | เก็บตำแหน่งเมาส์เพื่อเล็งและวาด crosshair |
| `GamePanel.mouseWheelMoved` | GamePanel | วนเปลี่ยนอาวุธ |
| `MainApplication.actionPerformed` | ปุ่ม Start/Quit, radio, combo, checkbox, timer | สร้าง `GameSettings`, เปิดเกม, อัปเดตพรีวิว |
| `MainApplication.stateChanged` | `volumeSlider`, `difficultySlider` | ปรับเสียงสด, แสดงผลจริงของระดับความยาก |
| `GameFrame.actionPerformed` | ปุ่ม Workshop/Pause/Quit, checkbox Auto-Fire/Hitbox | เปิด dialog, pause, ปรับโหมดสด |
| `UpgradeDialog.actionPerformed` / `valueChanged` | ปุ่ม Craft/Close, `recipeTable` | คราฟต์, อัปเดตรายละเอียดสูตร |

Extra component: `JTable` (สูตรคราฟต์, high score), `JSlider`, `JProgressBar`, `JTabbedPane`

---

## 6. งานที่เหลือ (เรียงตามความสำคัญ)

1. **เล่นจริงด้วยคน** ทั้ง 5 ด่าน: การทดสอบทั้งหมดใช้ input สังเคราะห์ ยังไม่มีใครจับเมาส์เล่นจริง
   ต้องดู feel ของการเล็ง, crosshair แทน cursor, และ balance (ผู้เล่นที่กดมั่วตายที่ ~35 วินาทีใน difficulty 3; ยืนเฉยตายที่ ~22–26 วินาทีใน difficulty 5 + Double Spawn)
2. **ฟังเสียง** ทั้ง 21 ไฟล์: ตรวจได้แค่ว่าโหลดและเล่นได้ ยังไม่มีใครฟังว่าไพเราะหรือดังเกินไปไหม ถ้าต้องแก้ใช้ `dev-tools/asset-tools/gen_sounds.py`
3. **ใส่ข้อมูลกลุ่มจริง**:
   - เปลี่ยน `XXX` เป็นรหัสตัวแทนกลุ่ม: rename โฟลเดอร์ `Project3_XXX`, แก้ `package Project3_XXX;` ทุกไฟล์, แก้ path string ใน `AssetLoader.java` และ `GameData.java` (ค้นคำว่า `Project3_XXX`), แก้ `pom.xml`
   - แก้ header 5 บรรทัดบนสุดของทุก `.java`, `GameData.GROUP_MEMBERS`, และ `readme.txt`
   - ชื่อที่ยาวเกิน ~28 ตัวอักษรจะถูกตัดในแผง Crew ของ launcher
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
9. **สิ่งที่นักศึกษาต้องทำเอง**: อัดคลิป `demo_clip.mp4` (< 5 นาที) ใส่ใน `Project3_XXX/` และ zip ส่ง

---

## 7. ข้อควรระวัง

- **exFAT + macOS**: ทุกครั้งที่เขียนไฟล์ จะเกิดไฟล์ขยะ `._*` ข้าง ๆ ลบก่อน zip ส่งเสมอ:
  `find "src" -name '._*' -delete` (กฎ `<resources>` ใน `pom.xml` จะ copy ไฟล์พวกนี้เข้า `target/classes` ด้วยถ้าไม่ลบ)
- **Git**: repo ผูกกับ `origin` บน GitHub และมี `Initial commit` หนึ่งอันซึ่งมีไฟล์ `._*` ติดไปด้วย
  งานทั้งหมดในเอกสารนี้ **ยังไม่ถูก commit** (`GameFrame/GamePanel/MainApplication/UpgradeDialog/readme.txt/pom.xml/HANDOFF.md/dev-tools/.gitignore` เป็น untracked, ไฟล์ model 6 ไฟล์เป็น modified) ห้าม commit/push โดยเจ้าของไม่ได้สั่ง
- **ลิขสิทธิ์ชุดภาพ** (`UPDATE 2_ZombiePack/LICENSE.txt`): ใช้ในโปรเจคได้ แต่ห้ามแจกจ่ายตัว asset แยก และห้ามใช้กับ AI image generator
- **harness ใน `dev-tools/`** มี path ของ scratchpad เดิมฝังอยู่ในสคริปต์ `run.sh`/`hrun.sh` และอ้างถึง stub ที่ไม่จำเป็นแล้ว ต้องแก้ path ก่อนใช้
  (compile harness คู่กับ 12 ไฟล์จริงได้เลย; `HeadlessSim` และ `RealWindow` อยู่ใน package `Project3_XXX` เพื่อเข้าถึงสมาชิก package-private)
- **ภาพผลการทดสอบ** (แผนที่ 5 ด่าน, เฟรม gameplay, ภาพ launcher/dialog) อยู่ในโฟลเดอร์ชั่วคราวซึ่งอาจถูกลบ:
  `/private/tmp/claude-501/-Users-tanawatgut--claude-spares-spare-79cmOx/96a86cfa-b7a6-4475-ba1d-800397290095/scratchpad/` (`phase1/`, `phase2b/`, `phase2c/`) สร้างใหม่ได้จาก harness
- **ข้อบังคับที่ห้ามพัง** (รายละเอียดใน `dev-tools/ENGINEERING_SPEC.md` ข้อ 1): ใช้แค่ `javax.swing`/`java.awt`/`javax.sound.sampled`/`javax.imageio`, ไม่มี library ภายนอก, frame ไม่เกิน 1200x700, source เป็น ASCII, ไม่มี absolute path, ไม่มี warning ภายใต้ `-Xlint:all`

---

## 8. วิธีทำงานที่ใช้มา

- Orchestrator (Fable 5.2): เขียน spec/สัญญา API, ตรวจภาพและ compile ซ้ำด้วยตัวเอง, ตัดสินใจเรื่อง design
- Developer (Opus 5.5): เฟส 1 = resources + เสียง + model layer + GameMap; เฟส 2B = GamePanel + GameFrame; เฟส 2C = MainApplication + UpgradeDialog (2B และ 2C ทำขนานกันโดย compile กับ stub ของอีกฝ่าย)
- ทุกเฟสต้องมี harness ที่รันได้จริงและภาพ render ที่เปิดดูได้ก่อนส่งงาน
