# Gradle Wrapper

`gradlew` 스크립트와 `gradle-wrapper.jar`는 바이너리 파일이므로
이 저장소에 포함되어 있지 않습니다.

## 최초 설정 방법

Gradle이 설치된 환경에서 아래 명령어로 wrapper를 생성하세요:

```bash
gradle wrapper --gradle-version 8.5
```

또는 Gradle 공식 사이트에서 직접 다운로드:
https://gradle.org/releases/

## CI 환경

Dockerfile에서 `eclipse-temurin:17-jdk-alpine` 이미지에
Gradle wrapper가 포함되어 자동으로 처리됩니다.
gradlew 실행 시 `gradle-wrapper.jar`가 없으면
`gradle/wrapper/gradle-wrapper.properties`의 `distributionUrl`에서
자동으로 다운로드합니다.
