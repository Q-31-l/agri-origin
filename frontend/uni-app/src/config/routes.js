/** 路由常量：页面里禁止直接写路径字符串 */
export const ROUTES = {
  LAUNCH: '/pages/launch/index',
  LOGIN: '/pages/auth/login',
  WX_BIND: '/pages/auth/wx-bind',
  PASSWORD: '/pages/auth/password',

  HOME: '/pages/tabbar/home',
  TAB_BATCH: '/pages/tabbar/batch',
  TAB_TRACE: '/pages/tabbar/trace',
  TAB_MINE: '/pages/tabbar/mine',

  BATCH_LIST: '/pages/batch/list',
  BATCH_DETAIL: '/pages/batch/detail',
  BATCH_CREATE: '/pages/batch/create',
  BATCH_EDIT: '/pages/batch/edit',

  PLANTING_LIST: '/pages/planting/list',
  PLANTING_FORM: '/pages/planting/form',
  HARVEST_FORM: '/pages/harvest/form',
  QUALITY_LIST: '/pages/quality/list',
  QUALITY_FORM: '/pages/quality/form',
  LOGISTICS_FORM: '/pages/logistics/form',
  SALES_FORM: '/pages/sales/form',
  ABNORMAL: '/pages/abnormal/index',
  CHAIN: '/pages/chain/index',
  TRACE: '/pages/trace/index',

  FORBIDDEN: '/pages/common/403',
  NOT_FOUND: '/pages/common/404',
  SETTINGS: '/pages/common/settings',

  PC_DASHBOARD: '/pages-pc/dashboard/index',
  PC_BATCH_LIST: '/pages-pc/batch/list',
  PC_BATCH_DETAIL: '/pages-pc/batch/detail',
  PC_PLANTING: '/pages-pc/planting/index',
  PC_HARVEST: '/pages-pc/harvest/index',
  PC_QUALITY: '/pages-pc/quality/index',
  PC_LOGISTICS: '/pages-pc/logistics/index',
  PC_SALES: '/pages-pc/sales/index',
  PC_ABNORMAL: '/pages-pc/abnormal/index',
  PC_CHAIN: '/pages-pc/chain/index',
  PC_TRACE: '/pages-pc/trace/index',
  PC_SYSTEM_USER: '/pages-pc/system/user',
  PC_SYSTEM_FARM: '/pages-pc/system/farm',
  PC_SYSTEM_PRODUCT: '/pages-pc/system/product',
  PC_SYSTEM_LOG: '/pages-pc/system/log'
}

/** 所有 tabBar 页面，必须与 pages.json 的 tabBar.list 完全一致 */
export const TAB_PAGES = [ROUTES.HOME, ROUTES.TAB_BATCH, ROUTES.TAB_TRACE, ROUTES.TAB_MINE]

/** PC 侧边菜单（与 pages.json 中 pages-pc 的注册保持同步） */
export const PC_MENUS = [
  { path: ROUTES.PC_DASHBOARD, title: '数据看板', icon: '▤' },
  { group: '全流程管理' },
  { path: ROUTES.PC_BATCH_LIST, title: '批次管理', icon: '▢', perm: 'batch:list' },
  { path: ROUTES.PC_PLANTING, title: '农事记录', icon: '❀', perm: 'planting:list' },
  { path: ROUTES.PC_HARVEST, title: '采收登记', icon: '✿', perm: 'harvest:list' },
  { path: ROUTES.PC_QUALITY, title: '质量检测', icon: '✓', perm: 'quality:list' },
  { path: ROUTES.PC_LOGISTICS, title: '物流运输', icon: '⇥', perm: 'logistics:list' },
  { path: ROUTES.PC_SALES, title: '销售管理', icon: '¥', perm: 'sales:list' },
  { group: '可信与监管' },
  { path: ROUTES.PC_ABNORMAL, title: '异常管控', icon: '!', perm: 'abnormal:list' },
  { path: ROUTES.PC_CHAIN, title: '链上存证', icon: '⛓', perm: 'chain:list' },
  { path: ROUTES.PC_TRACE, title: '溯源查询', icon: '⌕' },
  { group: '系统' },
  { path: ROUTES.PC_SYSTEM_USER, title: '用户与角色', icon: '☺', perm: 'user:list' },
  { path: ROUTES.PC_SYSTEM_FARM, title: '农场档案', icon: '⌂', perm: 'farm:list' },
  { path: ROUTES.PC_SYSTEM_PRODUCT, title: '产品品类', icon: '❖', perm: 'product:list' },
  { path: ROUTES.PC_SYSTEM_LOG, title: '操作日志', icon: '≡', perm: 'log:list' }
]
