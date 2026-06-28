<template>
  <div class="dashboard-view">
    <div class="dashboard-view__header">
      <h1>Dashboard</h1>
      <p class="dashboard-view__subtitle">Welcome back! Here's an overview of your system.</p>
    </div>

    <div class="dashboard-view__grid">
      <div class="dashboard-card">
        <div class="dashboard-card__icon dashboard-card__icon--blue">&#128202;</div>
        <div class="dashboard-card__content">
          <span class="dashboard-card__value">1,248</span>
          <span class="dashboard-card__label">Total Accounts</span>
        </div>
      </div>

      <div class="dashboard-card">
        <div class="dashboard-card__icon dashboard-card__icon--green">&#128176;</div>
        <div class="dashboard-card__content">
          <span class="dashboard-card__value">$3.2M</span>
          <span class="dashboard-card__label">Active Loans</span>
        </div>
      </div>

      <div class="dashboard-card">
        <div class="dashboard-card__icon dashboard-card__icon--purple">&#9881;</div>
        <div class="dashboard-card__content">
          <span class="dashboard-card__value">42</span>
          <span class="dashboard-card__label">Running Executions</span>
        </div>
      </div>

      <div class="dashboard-card">
        <div class="dashboard-card__icon dashboard-card__icon--orange">&#128197;</div>
        <div class="dashboard-card__content">
          <span class="dashboard-card__value">7</span>
          <span class="dashboard-card__label">Scheduled Tasks</span>
        </div>
      </div>
    </div>

    <div class="dashboard-view__section">
      <h2>Recent Activity</h2>
      <div class="dashboard-view__activity">
        <div class="activity-item">
          <span class="activity-item__dot activity-item__dot--green"></span>
          <div class="activity-item__content">
            <p class="activity-item__title">System backup completed</p>
            <span class="activity-item__time">2 minutes ago</span>
          </div>
        </div>
        <div class="activity-item">
          <span class="activity-item__dot activity-item__dot--blue"></span>
          <div class="activity-item__content">
            <p class="activity-item__title">New loan application #4521 received</p>
            <span class="activity-item__time">15 minutes ago</span>
          </div>
        </div>
        <div class="activity-item">
          <span class="activity-item__dot activity-item__dot--orange"></span>
          <div class="activity-item__content">
            <p class="activity-item__title">DSL rule updated by admin</p>
            <span class="activity-item__time">1 hour ago</span>
          </div>
        </div>
        <div class="activity-item">
          <span class="activity-item__dot activity-item__dot--purple"></span>
          <div class="activity-item__content">
            <p class="activity-item__title">Branch "Downtown" status changed to active</p>
            <span class="activity-item__time">3 hours ago</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue';

export default defineComponent({
  name: 'DashboardView',
});
</script>

<style scoped>
.dashboard-view {
  max-width: 1100px;
  margin: 0 auto;
}

.dashboard-view__header {
  margin-bottom: 28px;
}

.dashboard-view__header h1 {
  font-size: 26px;
  font-weight: 700;
  color: #0f172a;
  margin: 0 0 6px 0;
  letter-spacing: -0.02em;
}

.dashboard-view__subtitle {
  font-size: 14px;
  color: #64748b;
  margin: 0;
}

.dashboard-view__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
  margin-bottom: 32px;
}

.dashboard-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 20px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06), 0 1px 2px rgba(0, 0, 0, 0.04);
  border: 1px solid rgba(0, 0, 0, 0.04);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.dashboard-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.08);
}

.dashboard-card__icon {
  width: 44px;
  height: 44px;
  min-width: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
}

.dashboard-card__icon--blue {
  background: #eff6ff;
  color: #3b82f6;
}
.dashboard-card__icon--green {
  background: #f0fdf4;
  color: #22c55e;
}
.dashboard-card__icon--purple {
  background: #faf5ff;
  color: #a855f7;
}
.dashboard-card__icon--orange {
  background: #fff7ed;
  color: #f97316;
}

.dashboard-card__content {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.dashboard-card__value {
  font-size: 22px;
  font-weight: 700;
  color: #0f172a;
}

.dashboard-card__label {
  font-size: 13px;
  color: #64748b;
}

.dashboard-view__section {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06), 0 1px 2px rgba(0, 0, 0, 0.04);
  border: 1px solid rgba(0, 0, 0, 0.04);
}

.dashboard-view__section h2 {
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
  margin: 0 0 16px 0;
}

.dashboard-view__activity {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.activity-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid #f1f5f9;
}

.activity-item:last-child {
  border-bottom: none;
}

.activity-item__dot {
  width: 9px;
  height: 9px;
  min-width: 9px;
  border-radius: 50%;
  margin-top: 5px;
}

.activity-item__dot--green { background: #22c55e; }
.activity-item__dot--blue { background: #3b82f6; }
.activity-item__dot--orange { background: #f97316; }
.activity-item__dot--purple { background: #a855f7; }

.activity-item__content {
  flex: 1;
}

.activity-item__title {
  font-size: 14px;
  font-weight: 500;
  color: #334155;
  margin: 0 0 3px 0;
}

.activity-item__time {
  font-size: 12px;
  color: #94a3b8;
}
</style>
