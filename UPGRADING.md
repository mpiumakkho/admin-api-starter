# การรับอัปเดตจาก template

โปรเจกต์นี้ตั้งจาก access-control (`accessbase-docs`, schema_version 1) ก่อนที่ template จะมีโค้ดจริงให้ cherry-pick จึงมีแค่เอกสารและ contract ที่ใช้ร่วมกัน

เมื่อ template ออกรุ่นใหม่:

1. อ่าน ADR ใหม่ใน `docs/02-DECISIONS.md` ของ template แล้วเพิ่มลงในตาราง "ADR ของ template" ของโปรเจกต์นี้ ระบุว่าใช้ ไม่เกี่ยว หรือมี P-xxx ทับอยู่
2. เทียบ `contracts/*.yaml` ทีละไฟล์ อะไรที่เปลี่ยนใน template และไม่ขัดกับ P-xxx ให้ตามไป ส่วนที่ขัด ให้เขียน ADR ใหม่ก่อนตัดสินใจ
3. ถ้า template มีโค้ดของ foundation แล้ว ให้ cherry-pick เฉพาะ `accesscontrol/` ที่ไม่ชนกับ P-001–P-013 แล้วรัน `./mvnw verify` และ structure check

ทิศกลับก็เหมือนกัน: ถ้าสิ่งที่ทำในโปรเจกต์นี้ใช้ได้ทั่วไป เช่น session ต่ออุปกรณ์ (P-004), audit ใน transaction เดียวกัน (P-005) หรือ 2FA (P-008) ให้เสนอเข้า template เป็น ADR
