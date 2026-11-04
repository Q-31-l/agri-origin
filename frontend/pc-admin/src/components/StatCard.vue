<template>
  <div class="stat" :style="{ '--tone': color }">
    <div class="stat-top">
      <span class="stat-label">{{ label }}</span>
      <div class="stat-icon">
        <el-icon><component :is="icon" /></el-icon>
      </div>
    </div>
    <div class="stat-value">
      {{ value }}<span v-if="suffix" class="suffix">{{ suffix }}</span>
    </div>
    <div v-if="hint" class="stat-hint">{{ hint }}</div>
  </div>
</template>

<script setup>
defineProps({
  label: String,
  value: [Number, String],
  suffix: String,
  hint: String,
  icon: { type: String, default: 'DataLine' },
  color: { type: String, default: '#2f9e63' }
})
</script>

<style scoped>
.stat {
  background: #fff;
  border-radius: 14px;
  padding: 18px 20px;
  box-shadow: 0 6px 20px rgba(31, 61, 45, 0.05);
  border: 1px solid #edf2ef;
  position: relative;
  overflow: hidden;
}

.stat::after {
  content: '';
  position: absolute;
  right: -30px;
  bottom: -40px;
  width: 120px;
  height: 120px;
  border-radius: 50%;
  background: var(--tone);
  opacity: 0.06;
}

.stat-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.stat-label {
  color: #6b7c8f;
  font-size: 13px;
}

.stat-icon {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: color-mix(in srgb, var(--tone) 12%, white);
  color: var(--tone);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 17px;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  margin-top: 14px;
  letter-spacing: 0.5px;
  color: #1f2d3d;
}

.suffix {
  font-size: 13px;
  font-weight: 500;
  color: #6b7c8f;
  margin-left: 4px;
}

.stat-hint {
  font-size: 12px;
  color: #8b9aaa;
  margin-top: 6px;
}
</style>
