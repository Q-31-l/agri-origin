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

/**
 * 小程序端按「开发版 / 体验版 / 正式版」修正接口地址。
 *
 * 这里曾经写死了 staging.agri-origin.com / api.agri-origin.com 两个并不存在的域名，
 * 小程序打包后所有请求都会打到空气里（H5 因为走同域 /api 察觉不到）。
 *
 * 现在改成：默认就用 VITE_API_BASE（构建时注入，见 .env.production），
 * 只有当显式配置了 VITE_API_BASE_TRIAL / VITE_API_BASE_RELEASE 时，才按版本切换。
 * 这样"一套环境"只配一个变量，"前后端分离多环境"也不会被写死。
 */
function resolveApiBase() {
  const v = mpEnvVersion()
  if (v === 'trial' && import.meta.env.VITE_API_BASE_TRIAL) {
    return import.meta.env.VITE_API_BASE_TRIAL
  }
  if (v === 'release' && import.meta.env.VITE_API_BASE_RELEASE) {
    return import.meta.env.VITE_API_BASE_RELEASE
  }
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
