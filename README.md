# Augment Survival (증강 야생) - Minecraft 1.20.1 Fabric

## 빌드 방법
1. JDK 17 이상이 설치된 환경에서 프로젝트 루트(`augment-survival/`)로 이동합니다.
2. Gradle Wrapper 또는 로컬 Gradle을 사용하여 빌드합니다:
   - `./gradlew build` (Windows: `gradlew.bat build`)
3. 빌드 완료 후 `build/libs/augmentsurvival-1.0.0.jar` 파일을 마인크래프트 1.20.1 Fabric `mods/` 폴더에 넣고 실행합니다.

## 주요 명령어 및 단축키
- `/augment` 또는 `/augment start`: 로비 GUI(목표 선택/무작위 추첨, 참여자/관전자 지정) 열기
- `/augment start <ENDER_DRAGON|DIAMOND_100|WITHER|NETHERITE_16|RANDOM>`: 명령어로 즉시 게임 시작
- `/augment role <플레이어> <participant|spectator>`: 참여자 / 관전자 역할 지정
- `/augment codex`: 증강 도감 GUI 열기
- `/augment reroll_test`: 증강 선택 및 리롤(15% 등급 승급) 즉시 테스트 (OP 권한 필요)
- `G` 키: 숨긴 증강 선택창 다시 열기 / 선택 대기 카드가 없을 시 도감 열기
- `R` 키: 장착된 액티브 필살기 발동
- `V` 키: 장착된 액티브 필살기 슬롯 전환
