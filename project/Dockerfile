
FROM amazoncorretto:17-al2-jdk

# 2. 컨테이너 내부의 작업 디렉토리 설정
# 앞으로 실행될 모든 명령어의 기준 폴더가 됩니다.
WORKDIR /app

# 3. 내 컴퓨터의 JAR 파일을 도커 안으로 복사
# build/libs/ 폴더에 생성된 .jar 파일을 컨테이너 안의 app.jar라는 이름으로 가져옵니다.
COPY build/libs/*.jar app.jar

# 4. 우리가 처음에 정했던 'uploads' 폴더를 컨테이너 안에 생성
# 사용자가 올린 파일이 저장될 물리적 공간입니다.
RUN mkdir -p uploads

# 5. 서버 실행!
# java -jar app.jar 명령어를 입력하는 것과 같습니다.
ENTRYPOINT ["java", "-jar", "app.jar"]