# SonarQube Code Quality Analysis

## What is SonarQube?
SonarQube analyzes source code for:
- **Bugs** — code that will likely cause errors
- **Vulnerabilities** — security issues
- **Code Smells** — maintainability issues
- **Test Coverage** — % of code covered by tests
- **Duplications** — copy-pasted code blocks

## Quick Start

### 1. Start SonarQube
```bash
docker-compose -f docker-compose-sonar.yml up -d
```

### 2. Wait ~60 seconds then open
```
http://localhost:9000
Login: admin / admin
```

### 3. Generate coverage + run analysis
```bash
# From backend directory
mvn clean verify
mvn sonar:sonar -Dsonar.token=YOUR_TOKEN
```

### 4. Or run everything with one script
```bash
./run-sonar.sh
```

## Getting a Token

1. Login to http://localhost:9000
2. Go to: **My Account → Security → Generate Token**
3. Copy the token
4. Use it: `mvn sonar:sonar -Dsonar.token=YOUR_TOKEN`

## What to Check in Dashboard

| Metric | Target |
|---|---|
| Reliability | A (no bugs) |
| Security | A (no vulnerabilities) |
| Maintainability | A (few code smells) |
| Coverage | 60%+ |
| Duplications | under 3% |

## For Rapport de Stage
Take screenshots of:

1. **Dashboard overview** (the main metrics)
2. **Issues page** (bugs + vulnerabilities)
3. **Coverage page** (test coverage %)
4. **Code page** (showing analyzed files)
