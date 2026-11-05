// 全项目唯一读取环境变量的地方，其他文件一律从这里 import
const RAW = {
  env: import.meta.env.VITE_ENV || 'development',
  appName: import.meta.env.VITE_APP_NAME || '农安链溯源',
  version: import.meta.env.VITE_APP_VERSION || '1.0.0',
  apiBase: import.meta.env.VITE_API_BASE || '',
  chainEnabled: import.meta.env.VITE_CHAIN_ENABLED !== 'false',
  debug: import.meta.env.VITE_DEBUG === 'true',
  uploadMaxMb: Number(import.meta.env.VITE_UPLOAD_MAX_MB || 10)
}

/** 小程序端按「开发版 / 体验版 / 正式版」二次修正接口地址 */
function mpEnvVersion() {
  // #ifdef MP-WEIXIN
  try {
    return uni.getAccountInfoSync().miniProgram.envVersion
  } catch (e) {
    return 'develop'
  }
  // #endif
  // #ifndef MP-WEIXIN
  return ''
  // #endif
}

function resolveApiBase() {
  const v = mpEnvVersion()
  if (v === 'trial') return 'https://staging.agri-origin.com'
  if (v === 'release') return 'https://api.agri-origin.com'
  return RAW.apiBase
}

function buildBaseURL() {
  // #ifdef H5
  // H5：开发环境走 vite 代理（相对 /api），生产环境若未显式配置也走同域 /api
  if (!RAW.apiBase || RAW.env === 'production') return '/api'
  // #endif
  return `${resolveApiBase()}/api`
}

export const ENV = {
  ...RAW,
  apiBase: resolveApiBase(),
  baseURL: buildBaseURL(),
  /** 文件下载/预览的 origin（不带 /api 前缀） */
  get origin() {
    // #ifdef H5
    if (!RAW.apiBase || RAW.env === 'production') return ''
    // #endif
    return resolveApiBase()
  }
}

export const IS_DEV = ENV.env === 'development'
export const IS_PROD = ENV.env === 'production'
