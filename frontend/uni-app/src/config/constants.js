/** 业务常量：与后端 Constants.java / ResultCode.java 保持一致 */

export const ROLE = {
  ADMIN: 'ADMIN',
  REGULATOR: 'REGULATOR',
  PRODUCER: 'PRODUCER',
  VILLAGE_ADMIN: 'VILLAGE_ADMIN',
  FARMER: 'FARMER',
  INSPECTOR: 'INSPECTOR',
  LOGISTICS: 'LOGISTICS',
  SELLER: 'SELLER'
}

export const ROLE_NAME = {
  ADMIN: '系统管理员',
  REGULATOR: '监管人员',
  PRODUCER: '生产主体',
  VILLAGE_ADMIN: '村级管理员',
  FARMER: '农户',
  INSPECTOR: '检测机构',
  LOGISTICS: '物流企业',
  SELLER: '销售商'
}

/** 批次状态 → 文案 + 颜色（颜色遵循"异常用红、正常用绿、进行中用蓝"） */
export const BATCH_STATUS = {
  PLANTING: { text: '种植中', color: '#2563eb', bg: '#e8f0fe' },
  HARVESTED: { text: '已采收', color: '#0891b2', bg: '#e0f7fa' },
  QUALIFIED: { text: '检测合格', color: '#16a34a', bg: '#e8f7ee' },
  IN_TRANSIT: { text: '运输中', color: '#7c3aed', bg: '#f1eaff' },
  // 销售中原先取 #c2410c（橙红），和"异常管控"的红色在条形图里几乎分不出来，
  // 改成青绿：既区别于"检测合格"的草绿，也不会被误读成警示色
  ON_SALE: { text: '销售中', color: '#0f766e', bg: '#e3f4f1' },
  SOLD: { text: '已售罄', color: '#475569', bg: '#eef2f6' },
  ABNORMAL: { text: '异常管控', color: '#d93026', bg: '#fdeceb' },
  CLOSED: { text: '已关闭', color: '#6b7280', bg: '#f1f2f4' }
}

export const AUDIT_STATUS = {
  PENDING: { text: '待审核', color: '#c2410c', bg: '#fff1e6' },
  APPROVED: { text: '已通过', color: '#16a34a', bg: '#e8f7ee' },
  REJECTED: { text: '已驳回', color: '#d93026', bg: '#fdeceb' }
}

export const CHAIN_STATUS = {
  NONE: { text: '未存证', color: '#6b7280', bg: '#f1f2f4' },
  SUCCESS: { text: '已上链', color: '#16a34a', bg: '#e8f7ee' },
  FAILED: { text: '上链失败', color: '#d93026', bg: '#fdeceb' },
  SKIPPED: { text: '演示模式未上链', color: '#b45309', bg: '#fef6e0' }
}

/**
 * 存证事件类型：chain_record.event_type 实际存的是带后缀的长名（BATCH_REGISTER 等），
 * 这里同时保留短名，兼容时间轴等按业务类型传参的地方。
 * 之前只映射了短名，长名全部落空，列表里直接显示 "QUALITY_REPO…" 这种英文被截断的样子。
 */
export const EVENT_TYPE = {
  // 短名：业务类型
  BATCH: '批次建档',
  PLANTING: '农事操作',
  HARVEST: '采收',
  QUALITY: '质量检测',
  LOGISTICS: '物流运输',
  SALES: '销售',
  ABNORMAL: '异常',
  DISPOSAL: '处置',
  CORRECTION: '更正',
  CLOSE: '关闭',
  // 长名：存证流水实际写入值
  BATCH_REGISTER: '批次建档',
  BATCH_CLOSE: '批次结案',
  PLANTING_RECORD: '农事记录',
  HARVEST_RECORD: '采收登记',
  QUALITY_REPORT: '质检报告',
  QUALITY_FAIL: '质检不合格',
  LOGISTICS_HANDOVER: '物流交接',
  SALES_RECORD: '销售记录',
  DATA_CORRECTION: '数据更正',
  ABNORMAL_DISPOSAL: '异常处置'
}

/** 存证业务类型：与 chain_record.biz_type 对应，列表里替代英文原值 */
export const BIZ_TYPE = {
  BATCH: '批次',
  PLANTING: '农事',
  HARVEST: '采收',
  QUALITY: '质检',
  LOGISTICS: '物流',
  SALES: '销售',
  ABNORMAL: '异常',
  DISPOSAL: '处置',
  CORRECTION: '更正',
  CLOSE: '结案'
}

export const QUALITY_RESULT = {
  PASS: { text: '合格', color: '#16a34a', bg: '#e8f7ee' },
  FAIL: { text: '不合格', color: '#d93026', bg: '#fdeceb' },
  PENDING: { text: '待判定', color: '#c2410c', bg: '#fff1e6' }
}

/** 农事记录类型：与后端 BizPlantingRecord.recordType 枚举保持一致 */
export const PLANTING_TYPE = {
  SOIL: { text: '整地施肥' },
  SOWING: { text: '播种定植' },
  FERTILIZE: { text: '追肥' },
  IRRIGATE: { text: '灌溉' },
  PEST: { text: '病虫害防治' },
  WEED: { text: '除草' },
  OTHER: { text: '其他农事' }
}

/** 农事类型下拉项（value 为后端枚举码，label 为展示文案） */
export const PLANTING_TYPE_OPTIONS = Object.keys(PLANTING_TYPE).map((k) => ({
  value: k,
  label: PLANTING_TYPE[k].text
}))

/** 枚举码 → 中文 */
export function plantingTypeLabel(code) {
  const hit = PLANTING_TYPE[code]
  return hit ? hit.text : code || '-'
}

/**
 * 天气选项。采收登记会按当天实况自动填入其中一个，用户也能改。
 * 取值必须与后端 WeatherService.wmoText 的输出一一对应，否则自动填进来的
 * 值在下拉里找不到，会显示成空白。
 */
export const WEATHER_OPTIONS = [
  '晴',
  '多云',
  '阴',
  '小雨',
  '中雨',
  '大雨',
  '阵雨',
  '雷阵雨',
  '冻雨',
  '雪',
  '阵雪',
  '雾'
].map((v) => ({ value: v, label: v }))

/** 异常类型：与后端 dict(abnormal_type) 保持一致 */
export const ABNORMAL_TYPE = {
  PESTICIDE: { text: '农药残留超标' },
  HEAVY_METAL: { text: '重金属超标' },
  MICROBE: { text: '微生物超标' },
  OTHER: { text: '其他原因' }
}

export const ABNORMAL_TYPE_OPTIONS = Object.keys(ABNORMAL_TYPE).map((k) => ({
  value: k,
  label: ABNORMAL_TYPE[k].text
}))

export function abnormalTypeLabel(code) {
  const hit = ABNORMAL_TYPE[code]
  return hit ? hit.text : code || '-'
}

/** 异常处置单状态：HANDLING 处置中 / DONE 已完成 */
export const ABNORMAL_STATUS = {
  HANDLING: { text: '处置中', color: '#c2410c', bg: '#fff1e6' },
  DONE: { text: '已处置', color: '#16a34a', bg: '#e8f7ee' }
}

export function abnormalStatusLabel(code) {
  const hit = ABNORMAL_STATUS[code]
  return hit ? hit.text : code || '-'
}

/** 演示账号（登录页快捷填充） */
export const DEMO_ACCOUNTS = [
  { username: 'admin', label: '系统管理员' },
  { username: 'regulator01', label: '监管人员' },
  { username: 'producer01', label: '生产主体' },
  { username: 'village01', label: '村级管理员' },
  { username: 'farmer01', label: '农户' },
  { username: 'inspector01', label: '检测机构' },
  { username: 'logistics01', label: '物流企业' },
  { username: 'seller01', label: '销售商' }
]

export const DEMO_PASSWORD = '123456'

/** 字典类型（后端 /dict/{type}） */
export const DICT = {
  PRODUCT: 'product',
  FARM: 'farm',
  VILLAGE: 'village'
}
