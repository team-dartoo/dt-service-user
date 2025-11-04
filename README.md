# dt-service-user

## run
### 1. java build (로컬에 java 환경 구축이 안 된 경우)
```bash
docker run --rm -v $(pwd):/app -w /app amazoncorretto:21-alpine sh -c "./gradlew build -x test"
```
#### sh: ./gradlew: Permission denied 오류 발생 시
```bash 
chmod +x gradlew
```

### 2. docker build
```bash
docker compose build
```
### 3. docker run
```bash
docker compose up
```
#### docker network 생성이 안 된 경우
```bash
docker network create dt-network
```

### 4. check
```bash
curl -v http://localhost:8081
```
#### 응답 예시
```
* Host localhost:8081 was resolved.
* IPv6: ::1
* IPv4: 127.0.0.1
*   Trying [::1]:8081...
* Connected to localhost (::1) port 8081
> GET / HTTP/1.1
> Host: localhost:8081
> User-Agent: curl/8.7.1
> Accept: */*
> 
* Request completely sent off
< HTTP/1.1 401 
< Vary: Origin
< Vary: Access-Control-Request-Method
< Vary: Access-Control-Request-Headers
< WWW-Authenticate: Bearer
< X-Content-Type-Options: nosniff
< X-XSS-Protection: 0
< Cache-Control: no-cache, no-store, max-age=0, must-revalidate
< Pragma: no-cache
< Expires: 0
< X-Frame-Options: DENY
< Content-Length: 0
< Date: Tue, 04 Nov 2025 07:52:06 GMT
< 
* Connection #0 to host localhost left intact
```

