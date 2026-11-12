/**
 * 可自助注册的身份列表（注册页与 App 身份选择页共用，避免两处各写一份）。
 *
 * 顺序按溯源链走：农户先记农事 → 生产主体建批次 → 检测 → 物流 → 销售，
 * 最后是两类需要系统管理员复核才能启用的治理身份。
 * code 必须与后端 role 表一致，传错会注册成默认角色。
 */
export const REGISTER_ROLES = [
  {
    code: 'FARMER',
    name: '农户',
    icon: 'planting',
    hint: '填报农事与采收，只能看到被指派给自己的批次'
  },
  {
    code: 'PRODUCER',
    name: '生产主体',
    icon: 'farm',
    hint: '登记地块、建立批次，把农户和产品组织起来；需填合作社或家庭农场名称'
  },
  {
    code: 'INSPECTOR',
    name: '检测机构',
    icon: 'quality',
    hint: '上传质检报告并给出结论，决定批次能否流入市场；需填机构名称'
  },
  {
    code: 'LOGISTICS',
    name: '物流企业',
    icon: 'logistics',
    hint: '登记运输交接与冷链温度，运输环节逐条上链；需填企业名称'
  },
  {
    code: 'SELLER',
    name: '销售商',
    icon: 'sales',
    hint: '登记销售与批次核销，售出即完成溯源闭环；需填门店或企业名称'
  },
  {
    code: 'VILLAGE_ADMIN',
    name: '村级管理员',
    icon: 'lock',
    hint: '管理本村账号、审核农事与批次。需填真实身份证号，提交后由系统管理员审核通过才能启用'
  },
  {
    code: 'REGULATOR',
    name: '监管人员',
    icon: 'chain',
    hint: '全量监管与异常处置。需实名认证 + 所属监管部门，提交后由系统管理员审核通过才能启用'
  }
]

/** 需要系统管理员复核的身份：注册页上要额外提示"不能立即使用" */
export const REVIEW_ROLE_CODES = ['VILLAGE_ADMIN', 'REGULATOR']

/** 身份图标底色与图标色：与工作台快捷入口保持同一套配色 */
export const ROLE_TONE = {
  FARMER: { bg: '#e8f0fe', color: '#2563eb' },
  PRODUCER: { bg: '#e8f5ee', color: '#1f7a4d' },
  INSPECTOR: { bg: '#f1eaff', color: '#7c3aed' },
  LOGISTICS: { bg: '#e0f7fa', color: '#0891b2' },
  SELLER: { bg: '#fff1e6', color: '#c2410c' },
  VILLAGE_ADMIN: { bg: '#fef6e0', color: '#b45309' },
  REGULATOR: { bg: '#fdeceb', color: '#d93026' }
}
