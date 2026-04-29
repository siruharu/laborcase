package kr.laborcase.law.sync

/**
 * "이 법령이 개정됐는가?" 를 확인하는 주기 작업.
 *
 * Plan Task 0 discovery: 현재 OC 승인에 lsHistory 권한이 없어 원래 설계인
 * `/lawSearch.do?target=lsHistory` 호출은 불가능. 대신 `target=law` 검색을
 * 주기적으로 돌려 응답의 lsiSeq 가 DB 와 달라졌는지를 비교한다
 * (docs/research/drf-schema-notes.md §lsHistory 권한 누락).
 *
 * 이 비교 로직은 [FullSyncJob] 이 이미 idempotency 체크로 구현해 두었으므로
 * DeltaSyncJob 은 실행만 위임하고, sync_log.job_name 을 "delta-sync" 로
 * 구분한다.
 *
 * 운영상 의미:
 *   - FullSyncJob = 최초/재전개 목적, 매번 "no-op or 한 두 법령 import" 를 허용.
 *   - DeltaSyncJob = 일 1회 또는 임박 법령에 대해 시간 1회 폴링. 결과가
 *     항상 0 changes 인 것이 기대값. 1 이상이면 개정이 잡혔다는 뜻.
 */
class DeltaSyncJob(private val fullSyncJob: FullSyncJob) {

    fun run(): FullSyncJob.Result = fullSyncJob.run(JOB_NAME)

    companion object {
        const val JOB_NAME = "delta-sync"
    }
}
