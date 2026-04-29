# Cloud Monitoring observability — uptime checks + alert policies + email
# notification channel.
#
# Single email channel for MVP (분석 §P5-a). Sentry 가 application
# error 를 잡고, 여기는 service 자체 down 감지 + 비용 alert 채널.

# Email channel 은 첫 생성 시 verification email 을 보낸다. 사용자가
# 클릭해야 alert 가 실제로 발송됨. (terraform 은 channel 만 생성;
# verification 은 외부 절차).
resource "google_monitoring_notification_channel" "email" {
  display_name = "laborcase ops email"
  type         = "email"
  description  = "Single email channel for uptime alerts + billing budget alerts."

  labels = {
    email_address = var.alert_email
  }
}

# ── API liveness uptime check ──────────────────────────────────────────
# 1분 간격으로 /actuator/health/liveness 를 GET. 5분 연속 fail 시 alert.
# Cloud Run URL 의 host 부분만 추출 (https:// prefix 제거).

locals {
  api_host = replace(google_cloud_run_v2_service.api.uri, "https://", "")
}

resource "google_monitoring_uptime_check_config" "api_health" {
  display_name = "laborcase-api liveness probe"
  timeout      = "10s"
  period       = "60s"

  http_check {
    path           = "/actuator/health/liveness"
    port           = 443
    use_ssl        = true
    validate_ssl   = true
    request_method = "GET"
  }

  monitored_resource {
    type = "uptime_url"
    labels = {
      project_id = var.project_id
      host       = local.api_host
    }
  }

  # GCP 정책상 최소 3 region 필요. ASIA + USA + EUROPE 으로 분산해
  # false-positive 줄이고 region별 outage 영향 분리.
  selected_regions = ["ASIA_PACIFIC", "USA_OREGON", "EUROPE"]
}

# ── Alert policy: API 가 5분 이상 unreachable ──────────────────────────
# uptime check 실패 5분 누적 → email channel 로 알림.
# Cloud Monitoring 의 표준 패턴: check_passed 메트릭이 false 인 횟수를
# 1분 alignment 로 reduce_count_false 한 뒤 1회 이상이 5분 지속되면 fire.

resource "google_monitoring_alert_policy" "api_down" {
  display_name = "laborcase API uptime check failure"
  combiner     = "OR"

  conditions {
    display_name = "API liveness check failing"

    condition_threshold {
      filter          = "metric.type=\"monitoring.googleapis.com/uptime_check/check_passed\" AND resource.type=\"uptime_url\" AND metric.label.check_id=\"${google_monitoring_uptime_check_config.api_health.uptime_check_id}\""
      duration        = "300s"
      comparison      = "COMPARISON_GT"
      threshold_value = 1

      aggregations {
        alignment_period     = "60s"
        per_series_aligner   = "ALIGN_NEXT_OLDER"
        cross_series_reducer = "REDUCE_COUNT_FALSE"
        group_by_fields      = ["resource.label.project_id", "resource.label.host"]
      }
    }
  }

  notification_channels = [google_monitoring_notification_channel.email.id]

  alert_strategy {
    auto_close = "1800s" # 30분 idle 후 자동 close
  }

  documentation {
    content   = "laborcase API (asia-northeast3) 의 /actuator/health/liveness 가 5분간 연속 실패. Cloud Run revision 상태와 Cloud SQL 연결 상태를 확인하세요. Runbook: docs/runbooks/deploy.md"
    mime_type = "text/markdown"
  }
}

# ── Billing budget alert (분석 §P5-b) ────────────────────────────────
# 월 예산 $50, 60/80/100% threshold 의 의도. terraform 자동화 시도 후
# 모든 변수 형식 점검 (units string / projects path / channel) 했음에도
# billingbudgets.googleapis.com 가 specifc field 를 명시 안 한 채
# "Request contains an invalid argument" 400 으로 거부.
#
# 잠정 결정: budget 은 GCP Console 에서 사용자 manual 5분 셋업으로
# 처리. 절차는 docs/runbooks/deploy.md (DT-Task 15) 에 박제 예정.
# 절차 요약:
#   Cloud Console → Billing → Budgets & alerts → Create budget
#     name = "laborcase monthly budget 50 USD"
#     budget type = Specified amount, $50 USD
#     scope = Project: laborcase-prod
#     thresholds = Actual 60%, 80%, 100% + Forecasted 100%
#     email recipients = siru.haru7419@gmail.com (billing user 자동)

# resource "google_billing_budget" "monthly" {
#   billing_account = var.billing_account_id
#   ...
# }
