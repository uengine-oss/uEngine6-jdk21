<template>
  <div class="analytics-dashboard">
    <div class="dashboard-header">
      <div>
        <h1>프로세스 분석</h1>
        <p>PostgreSQL 분석 스키마에 적재된 프로세스 실행 지표입니다.</p>
      </div>
      <div class="dashboard-actions">
        <label>
          시작일
          <input v-model="from" type="date">
        </label>
        <label>
          종료일
          <input v-model="to" type="date">
        </label>
        <md-button class="md-raised" :disabled="loading || runningEtl" @click="loadDashboard">
          조회
        </md-button>
        <md-button class="md-raised md-primary" :disabled="loading || runningEtl" @click="runEtl">
          {{ runningEtl ? 'ETL 실행 중...' : 'ETL 갱신' }}
        </md-button>
      </div>
    </div>

    <div v-if="error" class="error-message">{{ error }}</div>
    <div v-if="loading" class="loading-message">분석 데이터를 불러오는 중입니다.</div>

    <template v-else>
      <div class="metric-grid">
        <section class="metric-card accent-blue">
          <span>프로세스</span>
          <strong>{{ formatNumber(summary.processCount) }}</strong>
          <small>선택 기간 시작 건수</small>
        </section>
        <section class="metric-card accent-green">
          <span>완료율</span>
          <strong>{{ formatPercent(summary.completionRate) }}</strong>
          <small>{{ formatNumber(summary.completedProcessCount) }}건 완료</small>
        </section>
        <section class="metric-card accent-purple">
          <span>평균 처리시간</span>
          <strong>{{ formatDuration(summary.averageDurationSeconds) }}</strong>
          <small>진행 중 건 포함</small>
        </section>
        <section class="metric-card accent-orange">
          <span>재작업률</span>
          <strong>{{ formatPercent(summary.reworkRate) }}</strong>
          <small>{{ formatNumber(summary.reworkTaskCount) }}개 재작업 태스크</small>
        </section>
      </div>

      <div class="dashboard-grid">
        <section class="panel">
          <h2>상태별 프로세스</h2>
          <div v-if="!dashboard.statuses.length" class="empty-state">표시할 데이터가 없습니다.</div>
          <div v-for="item in dashboard.statuses" :key="item.status" class="bar-row">
            <span class="bar-label">{{ statusLabel(item.status) }}</span>
            <div class="bar-track">
              <div class="bar-fill status-bar" :style="{ width: barWidth(item.count, maxStatusCount) }"></div>
            </div>
            <strong>{{ formatNumber(item.count) }}</strong>
          </div>
        </section>

        <section class="panel">
          <h2>태스크 구성</h2>
          <div class="task-total">총 {{ formatNumber(summary.totalTaskCount) }}개</div>
          <div class="task-stack">
            <div class="human-task" :style="{ width: taskWidth(summary.humanTaskCount) }"></div>
            <div class="automated-task" :style="{ width: taskWidth(summary.automatedTaskCount) }"></div>
          </div>
          <div class="legend-row">
            <span><i class="legend human"></i>사람 태스크</span>
            <strong>{{ formatNumber(summary.humanTaskCount) }}</strong>
          </div>
          <div class="legend-row">
            <span><i class="legend automated"></i>자동화 태스크</span>
            <strong>{{ formatNumber(summary.automatedTaskCount) }}</strong>
          </div>
        </section>
      </div>

      <section class="panel daily-panel">
        <h2>일별 시작 프로세스</h2>
        <div v-if="!dashboard.daily.length" class="empty-state">표시할 데이터가 없습니다.</div>
        <div v-else class="daily-chart">
          <div v-for="item in dashboard.daily" :key="item.date" class="daily-column">
            <div class="daily-count">{{ item.processCount }}</div>
            <div class="daily-bar-wrap">
              <div class="daily-bar" :style="{ height: barHeight(item.processCount, maxDailyCount) }"></div>
            </div>
            <span>{{ shortDate(item.date) }}</span>
          </div>
        </div>
      </section>

      <section class="panel process-panel">
        <h2>프로세스별 상위 지표</h2>
        <div class="table-wrap">
          <table>
            <thead>
            <tr>
              <th>프로세스</th>
              <th>실행</th>
              <th>완료</th>
              <th>평균 처리시간</th>
              <th>재작업</th>
            </tr>
            </thead>
            <tbody>
            <tr v-if="!dashboard.processes.length">
              <td colspan="5" class="empty-state">표시할 데이터가 없습니다.</td>
            </tr>
            <tr v-for="item in dashboard.processes" :key="item.processKey">
              <td>{{ item.processName }}</td>
              <td>{{ formatNumber(item.processCount) }}</td>
              <td>{{ formatNumber(item.completedProcessCount) }}</td>
              <td>{{ formatDuration(item.averageDurationSeconds) }}</td>
              <td>{{ formatNumber(item.reworkTaskCount) }}</td>
            </tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>
  </div>
</template>

<script>
  function localDateString(date) {
    var year = date.getFullYear();
    var month = String(date.getMonth() + 1).padStart(2, '0');
    var day = String(date.getDate()).padStart(2, '0');
    return year + '-' + month + '-' + day;
  }

  function emptyDashboard() {
    return {
      summary: {
        processCount: 0,
        completedProcessCount: 0,
        activeProcessCount: 0,
        averageDurationSeconds: 0,
        totalTaskCount: 0,
        humanTaskCount: 0,
        automatedTaskCount: 0,
        reworkTaskCount: 0,
        completionRate: 0,
        reworkRate: 0
      },
      statuses: [],
      daily: [],
      processes: []
    };
  }

  export default {
    name: 'AnalyticsDashboard',
    data() {
      var to = new Date();
      var from = new Date();
      from.setDate(from.getDate() - 29);
      return {
        from: localDateString(from),
        to: localDateString(to),
        loading: false,
        runningEtl: false,
        error: '',
        dashboard: emptyDashboard()
      };
    },
    computed: {
      summary() {
        return this.dashboard.summary || emptyDashboard().summary;
      },
      maxStatusCount() {
        return Math.max.apply(null, [1].concat(this.dashboard.statuses.map(item => Number(item.count) || 0)));
      },
      maxDailyCount() {
        return Math.max.apply(null, [1].concat(this.dashboard.daily.map(item => Number(item.processCount) || 0)));
      },
      apiBase() {
        if (window.analyticsApiUrl) {
          return window.analyticsApiUrl.replace(/\/$/, '');
        }
        if (window.config && window.config.analyticsApiUrl) {
          return window.config.analyticsApiUrl.replace(/\/$/, '');
        }
        return window.backend.$bind.ref.replace(/\/$/, '');
      }
    },
    mounted() {
      this.loadDashboard();
    },
    methods: {
      loadDashboard() {
        var me = this;
        me.loading = true;
        me.error = '';
        me.$http.get(me.apiBase + '/api/analytics/dashboard', {
          params: {from: me.from, to: me.to}
        }).then(function (response) {
          me.dashboard = response.body || emptyDashboard();
          me.loading = false;
        }, function (response) {
          me.error = me.errorMessage(response, '분석 데이터를 불러오지 못했습니다.');
          me.loading = false;
        });
      },
      runEtl() {
        var me = this;
        me.runningEtl = true;
        me.error = '';
        me.$http.post(me.apiBase + '/api/analytics/etl/run').then(function () {
          me.runningEtl = false;
          me.loadDashboard();
        }, function (response) {
          me.error = me.errorMessage(response, 'ETL 실행에 실패했습니다.');
          me.runningEtl = false;
        });
      },
      errorMessage(response, fallback) {
        if (response && response.body && (response.body.message || response.body.error)) {
          return response.body.message || response.body.error;
        }
        return fallback;
      },
      formatNumber(value) {
        return Number(value || 0).toLocaleString();
      },
      formatPercent(value) {
        return Number(value || 0).toFixed(1) + '%';
      },
      formatDuration(value) {
        var seconds = Number(value || 0);
        if (seconds >= 86400) return (seconds / 86400).toFixed(1) + '일';
        if (seconds >= 3600) return (seconds / 3600).toFixed(1) + '시간';
        if (seconds >= 60) return Math.round(seconds / 60) + '분';
        return Math.round(seconds) + '초';
      },
      statusLabel(status) {
        var labels = {
          COMPLETED: '완료',
          RUNNING: '진행 중',
          NEW: '신규',
          CANCELLED: '취소',
          UNKNOWN: '상태 없음'
        };
        return labels[status] || status;
      },
      barWidth(value, max) {
        return Math.max(3, (Number(value || 0) / max) * 100) + '%';
      },
      barHeight(value, max) {
        return Math.max(4, (Number(value || 0) / max) * 120) + 'px';
      },
      taskWidth(value) {
        var classified = Number(this.summary.humanTaskCount || 0) + Number(this.summary.automatedTaskCount || 0);
        return classified ? (Number(value || 0) / classified) * 100 + '%' : '0%';
      },
      shortDate(value) {
        return value ? value.substring(5).replace('-', '/') : '';
      }
    }
  };
</script>

<style scoped>
  .analytics-dashboard { padding: 28px; color: #263238; background: #f4f7fa; min-height: 100%; box-sizing: border-box; }
  .dashboard-header { display: flex; justify-content: space-between; align-items: flex-end; gap: 24px; margin-bottom: 24px; }
  .dashboard-header h1 { margin: 0 0 6px; font-size: 28px; font-weight: 500; }
  .dashboard-header p { margin: 0; color: #71808e; }
  .dashboard-actions { display: flex; align-items: flex-end; gap: 10px; flex-wrap: wrap; }
  .dashboard-actions label { display: flex; flex-direction: column; gap: 5px; color: #607d8b; font-size: 12px; }
  .dashboard-actions input { height: 36px; padding: 0 10px; border: 1px solid #cfd8dc; border-radius: 4px; background: white; }
  .metric-grid { display: grid; grid-template-columns: repeat(4, minmax(170px, 1fr)); gap: 16px; margin-bottom: 16px; }
  .metric-card, .panel { background: white; border-radius: 8px; box-shadow: 0 2px 10px rgba(38, 50, 56, .08); }
  .metric-card { padding: 20px; border-top: 4px solid; }
  .metric-card span, .metric-card small { display: block; color: #78909c; }
  .metric-card strong { display: block; margin: 10px 0 6px; font-size: 27px; font-weight: 500; }
  .accent-blue { border-color: #42a5f5; } .accent-green { border-color: #66bb6a; }
  .accent-purple { border-color: #7e57c2; } .accent-orange { border-color: #ffa726; }
  .dashboard-grid { display: grid; grid-template-columns: 2fr 1fr; gap: 16px; margin-bottom: 16px; }
  .panel { padding: 20px; }
  .panel h2 { margin: 0 0 18px; font-size: 17px; font-weight: 500; }
  .bar-row { display: grid; grid-template-columns: 90px 1fr 48px; gap: 12px; align-items: center; margin: 13px 0; }
  .bar-label { color: #546e7a; }
  .bar-track { height: 10px; border-radius: 6px; background: #edf1f4; overflow: hidden; }
  .bar-fill { height: 100%; border-radius: 6px; transition: width .25s ease; }
  .status-bar { background: linear-gradient(90deg, #42a5f5, #26c6da); }
  .task-total { color: #78909c; margin-bottom: 12px; }
  .task-stack { display: flex; height: 18px; border-radius: 9px; overflow: hidden; background: #eceff1; margin-bottom: 22px; }
  .human-task { background: #42a5f5; } .automated-task { background: #7e57c2; }
  .legend-row { display: flex; justify-content: space-between; margin: 12px 0; }
  .legend { display: inline-block; width: 9px; height: 9px; border-radius: 50%; margin-right: 8px; }
  .legend.human { background: #42a5f5; } .legend.automated { background: #7e57c2; }
  .daily-panel, .process-panel { margin-bottom: 16px; }
  .daily-chart { display: flex; gap: 10px; min-height: 165px; overflow-x: auto; align-items: flex-end; padding-top: 8px; }
  .daily-column { min-width: 34px; flex: 1; text-align: center; color: #78909c; font-size: 11px; }
  .daily-count { margin-bottom: 4px; color: #546e7a; }
  .daily-bar-wrap { height: 120px; display: flex; align-items: flex-end; justify-content: center; }
  .daily-bar { width: 70%; max-width: 30px; background: linear-gradient(#26c6da, #42a5f5); border-radius: 4px 4px 0 0; }
  .table-wrap { overflow-x: auto; }
  table { width: 100%; border-collapse: collapse; }
  th, td { padding: 12px 14px; border-bottom: 1px solid #eceff1; text-align: right; }
  th { color: #78909c; font-weight: 500; background: #fafbfc; }
  th:first-child, td:first-child { text-align: left; }
  .empty-state, .loading-message { padding: 28px; color: #90a4ae; text-align: center; }
  .error-message { margin-bottom: 16px; padding: 12px 16px; border-radius: 4px; color: #b71c1c; background: #ffebee; }
  @media (max-width: 960px) {
    .dashboard-header { align-items: stretch; flex-direction: column; }
    .metric-grid { grid-template-columns: repeat(2, 1fr); }
    .dashboard-grid { grid-template-columns: 1fr; }
  }
  @media (max-width: 560px) {
    .analytics-dashboard { padding: 16px; }
    .metric-grid { grid-template-columns: 1fr; }
  }
</style>
