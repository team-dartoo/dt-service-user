# k8s 구조 재편 계획

## 배경

- test / prod 환경을 namespace 단위로 분리
- 환경별 config, secret을 독립적으로 관리
- DB, Gateway 등 인프라 리소스는 별도 infra 레포에서 관리

---

## 목표 디렉토리 구조

```
k8s/
├── base/
│   ├── kustomization.yaml     # deployment, service, httproute만 포함
│   ├── deployment.yaml
│   ├── service.yaml
│   └── httproute.yaml
└── overlays/
    ├── prod/
    │   ├── kustomization.yaml  # namespace: dartoo / replicas: 2
    │   ├── configmap.yaml      # prod 전용 ConfigMap
    │   └── secret.yaml         # prod 전용 Secret → 추후 SealedSecret으로 교체
    └── test/
        ├── kustomization.yaml  # namespace: dt-test / replicas: 1 / 리소스 축소
        ├── configmap.yaml      # test 전용 ConfigMap
        └── secret.yaml         # test 전용 Secret → 추후 SealedSecret으로 교체
```

### 배포 명령

```bash
# prod 배포
kubectl apply -k k8s/overlays/prod

# test 배포
kubectl apply -k k8s/overlays/test
```

---

## 환경별 설정 차이

| 항목 | prod | test |
|------|------|------|
| namespace | `dartoo` | `dt-test` |
| replicas | 2 | 1 |
| CPU request/limit | 250m / 500m | 100m / 200m |
| Memory request/limit | 512Mi / 1Gi | 256Mi / 512Mi |
| SPRING_PROFILES_ACTIVE | `product` | `test` |
| DB_URL | `mysql-svc.dartoo` | `mysql-svc.dt-test` |
| Secret 관리 | SealedSecret (예정) | SealedSecret (예정) |

---

## 작업 체크리스트

### 이 레포 (dt-service-user)

- [x] `k8s/base/deployment.yaml` 생성
- [x] `k8s/base/service.yaml` 생성
- [x] `k8s/base/httproute.yaml` 생성
- [x] `k8s/base/kustomization.yaml` 수정 (configmap 제거)
- [x] `k8s/base/configmap.yaml` 삭제 (overlay로 이동)
- [x] `k8s/overlays/prod/kustomization.yaml` 작성
- [x] `k8s/overlays/prod/configmap.yaml` 작성
- [x] `k8s/overlays/prod/secret.yaml` 작성
- [x] `k8s/overlays/test/kustomization.yaml` 작성
- [x] `k8s/overlays/test/configmap.yaml` 작성
- [x] `k8s/overlays/test/secret.yaml` 작성 (CHANGE_ME placeholder)
- [x] `k8s/app/` 디렉토리 삭제 (기존 파일 정리)

### infra 레포로 이동

- [ ] `k8s/db/` → infra 레포 (prod: namespace `dartoo` / test: namespace `dt-test`)
- [ ] `k8s/gateway/` → infra 레포
- [ ] `k8s/ghcr-secret.yaml` → infra 레포

---

## 의존성 주의사항

### ghcr-secret
- `deployment.yaml`의 `imagePullSecrets`에서 참조
- infra 레포에서 `dartoo`, `dt-test` namespace 양쪽에 모두 생성해야 함

### mysql-secret
- `deployment.yaml`에서 `DB_USERNAME`, `DB_PASSWORD` 참조
- infra 레포에서 각 namespace의 DB StatefulSet과 함께 생성해야 함

### envoy-gateway
- `httproute.yaml`의 `parentRefs`에서 참조
- infra 레포의 Gateway가 먼저 배포되어 있어야 함

---

## SealedSecret 전환 계획

현재는 일반 `kind: Secret`으로 관리하며, 추후 아래 절차로 전환

```bash
# prod
kubeseal --namespace dartoo < overlays/prod/secret.yaml > overlays/prod/sealed-secret.yaml

# test
kubeseal --namespace dt-test < overlays/test/secret.yaml > overlays/test/sealed-secret.yaml
```

전환 후 `secret.yaml`은 삭제하고 `.gitignore`에 추가
