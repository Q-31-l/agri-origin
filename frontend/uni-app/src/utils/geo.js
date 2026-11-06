/**
 * 设备定位：一次实现，三端通吃。
 *
 * 为什么不用 IP 定位当主路径：
 * IP 归属地只到城市级，且在「手机 4G / 代理 / 云出口」下经常落到省会甚至外省，
 * 拿它当"作业地块位置"是错得离谱的。这里优先用系统定位（GPS / WiFi 混合），
 * 精度到米级；只有定位被拒时才由后端退到 IP 兜底，并且明确标注来源。
 *
 * 坐标系：统一 GCJ02（国内地图标准）。
 * H5 的 navigator.geolocation 返回 WGS84，直接存会和 App/小程序 采集到的坐标
 * 差几百米，所以这里统一做一次 WGS84 → GCJ02 转换后再返回。
 */

const CACHE_TTL = 5 * 60 * 1000

let cached = null

/** 上次定位结果（5 分钟内复用，避免每开一次表单就弹一次授权） */
export function cachedLocation() {
  if (cached && Date.now() - cached.at < CACHE_TTL) return cached
  return null
}

export function clearLocationCache() {
  cached = null
}

/**
 * @param {{ timeout?: number, force?: boolean }} opts
 * @returns {Promise<{lat:number,lng:number,accuracy:number,source:string,at:number}>}
 */
export function locate(opts = {}) {
  const timeout = opts.timeout || 8000
  if (!opts.force) {
    const hit = cachedLocation()
    if (hit) return Promise.resolve(hit)
  }

  // #ifdef H5
  return locateH5(timeout)
  // #endif
  // #ifndef H5
  return locateNative(timeout)
  // #endif
}

/** 坐标文本："23.131234,113.264567"（数据库 gps 字段就是这格式） */
export function formatCoord(lat, lng) {
  if (lat == null || lng == null) return ''
  return `${Number(lat).toFixed(6)},${Number(lng).toFixed(6)}`
}

/** 把失败原因翻译成用户能照着做的一句话 */
export function explainLocationError(err) {
  const name = (err && (err.name || err.code)) || ''
  const msg = String((err && err.message) || err || '')
  if (name === 'NotAllowedError' || name === 1 || /denied|permission/i.test(msg)) {
    return '定位权限被拒绝，已改用农场档案地址'
  }
  if (name === 'PositionUnavailable' || name === 2 || /unavailable/i.test(msg)) {
    return '暂时取不到定位信号，已改用农场档案地址'
  }
  if (name === 'TimeoutError' || name === 3 || /timeout/i.test(msg)) {
    return '定位超时，已改用农场档案地址'
  }
  return '定位不可用，已改用农场档案地址'
}

// #ifdef H5
function locateH5(timeout) {
  return new Promise((resolve, reject) => {
    if (typeof navigator === 'undefined' || !navigator.geolocation) {
      reject(new Error('当前浏览器不支持定位'))
      return
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        // 浏览器给的是 WGS84，转成 GCJ02 与 App/小程序 对齐
        const gcj = wgs84ToGcj02(pos.coords.latitude, pos.coords.longitude)
        cached = {
          lat: gcj.lat,
          lng: gcj.lng,
          accuracy: Math.round(pos.coords.accuracy || 0),
          source: 'GPS',
          at: Date.now()
        }
        resolve(cached)
      },
      (err) => reject(err),
      { enableHighAccuracy: true, timeout, maximumAge: 60000 }
    )
  })
}
// #endif

// #ifndef H5
function locateNative(timeout) {
  return new Promise((resolve, reject) => {
    uni.getLocation({
      type: 'gcj02',
      isHighAccuracy: true,
      highAccuracyExpireTime: timeout,
      success: (res) => {
        cached = {
          lat: res.latitude,
          lng: res.longitude,
          accuracy: Math.round(res.accuracy || 0),
          source: 'GPS',
          at: Date.now()
        }
        resolve(cached)
      },
      fail: (err) => reject(err)
    })
  })
}
// #endif

/* ---------------- WGS84 → GCJ02 ---------------- */

const PI = 3.1415926535897932384626
const AXIS = 6378245.0
const EE = 0.00669342162296594323

function outOfChina(lat, lng) {
  return lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271
}

function transformLat(x, y) {
  let ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x))
  ret += ((20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0) / 3.0
  ret += ((20.0 * Math.sin(y * PI) + 40.0 * Math.sin((y / 3.0) * PI)) * 2.0) / 3.0
  ret += ((160.0 * Math.sin((y / 12.0) * PI) + 320 * Math.sin((y * PI) / 30.0)) * 2.0) / 3.0
  return ret
}

function transformLng(x, y) {
  let ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x))
  ret += ((20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0) / 3.0
  ret += ((20.0 * Math.sin(x * PI) + 40.0 * Math.sin((x / 3.0) * PI)) * 2.0) / 3.0
  ret += ((150.0 * Math.sin((x / 12.0) * PI) + 300.0 * Math.sin((x / 30.0) * PI)) * 2.0) / 3.0
  return ret
}

/** 境外坐标不做偏移（GCJ02 只在中国大陆范围内生效） */
export function wgs84ToGcj02(lat, lng) {
  if (outOfChina(lat, lng)) return { lat, lng }
  let dLat = transformLat(lng - 105.0, lat - 35.0)
  let dLng = transformLng(lng - 105.0, lat - 35.0)
  const radLat = (lat / 180.0) * PI
  let magic = Math.sin(radLat)
  magic = 1 - EE * magic * magic
  const sqrtMagic = Math.sqrt(magic)
  dLat = (dLat * 180.0) / (((AXIS * (1 - EE)) / (magic * sqrtMagic)) * PI)
  dLng = (dLng * 180.0) / ((AXIS / sqrtMagic) * Math.cos(radLat) * PI)
  return { lat: lat + dLat, lng: lng + dLng }
}
