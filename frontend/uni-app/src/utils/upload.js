import { ENV } from '@/config/env'
import { getToken } from '@/utils/auth'
import { toast } from '@/utils/ui'

/**
 * 上传单个文件到后端 /file/upload
 * @returns {Promise<{id,fileName,filePath,fileHash,fileSize}>}
 */
export function uploadFile({ filePath, bizType, bizId, name = 'file', onProgress }) {
  return new Promise((resolve, reject) => {
    const token = getToken()
    const task = uni.uploadFile({
      url: `${ENV.baseURL}/file/upload`,
      filePath,
      name,
      formData: {
        ...(bizType ? { bizType } : {}),
        ...(bizId ? { bizId: String(bizId) } : {})
      },
      // 只带 Authorization，绝不能加 Content-Type（小程序会丢 boundary）
      header: token ? { Authorization: `Bearer ${token}` } : {},
      success(res) {
        let body
        try {
          body = typeof res.data === 'string' ? JSON.parse(res.data) : res.data
        } catch (e) {
          return reject({ code: -2, message: '上传响应解析失败' })
        }
        if (res.statusCode === 401) return reject({ code: 401, message: '登录已失效' })
        if (res.statusCode < 200 || res.statusCode >= 300 || body.code !== 0) {
          const msg = body.message || messageOfUpload(res.statusCode)
          toast(msg)
          return reject({ code: body.code || res.statusCode, message: msg })
        }
        resolve(body.data)
      },
      fail(err) {
        if (ENV.debug) console.error('[upload fail]', err)
        toast('上传失败，请检查网络')
        reject({ code: -1, message: '上传失败', raw: err })
      }
    })

    if (onProgress && task && task.onProgressUpdate) {
      task.onProgressUpdate((e) => onProgress(e.progress))
    }
  })
}

function messageOfUpload(status) {
  if (status === 413) return '文件过大，请压缩后重试'
  if (status === 400) return '文件类型不支持，仅允许 jpg/png/webp/pdf/mp4/mov/webm'
  return '上传失败'
}

/** 选择视频（录像/相册），返回 { path, size, duration, type:'video' } 列表 */
export function chooseVideos({ maxDuration = 60, sourceType = ['camera', 'album'] } = {}) {
  return new Promise((resolve, reject) => {
    uni.chooseVideo({
      sourceType,
      compressed: true,
      maxDuration,
      camera: 'back',
      success: (res) => {
        const path = res.tempFilePath
        if (!path) return reject({ message: '未选择视频' })
        resolve([{ path, size: res.size || 0, duration: res.duration || 0, type: 'video' }])
      },
      fail: (err) => {
        if (/cancel/i.test(err.errMsg || '')) return reject({ cancel: true })
        toast('无法访问相机或相册，请检查授权')
        reject(err)
      }
    })
  })
}

/** 判断路径/文件名是否为视频 */
export function isVideo(name) {
  return /\.(mp4|mov|m4v|webm|avi)(\?|$)/i.test(String(name || ''))
}

/** 串行批量上传，避免小程序并发限制 */
export async function uploadFiles(paths, { bizType, bizId, onItemProgress } = {}) {
  const results = []
  for (let i = 0; i < paths.length; i++) {
    const r = await uploadFile({
      filePath: paths[i],
      bizType,
      bizId,
      onProgress: (p) => onItemProgress && onItemProgress(i, p)
    })
    results.push(r)
  }
  return results
}

/** 选择图片（相册/拍照） */
export function chooseImages(count = 3, sourceType = ['camera', 'album']) {
  return new Promise((resolve, reject) => {
    uni.chooseImage({
      count,
      sizeType: ['compressed'],
      sourceType,
      success: (res) => resolve(res.tempFilePaths || []),
      fail: (err) => {
        if (/cancel/i.test(err.errMsg || '')) return reject({ cancel: true })
        toast('无法访问相机或相册，请检查授权')
        reject(err)
      }
    })
  })
}

/** 选择文件（H5 / App 支持；小程序用 chooseMessageFile） */
export function chooseFile() {
  return new Promise((resolve, reject) => {
    // #ifdef MP-WEIXIN
    uni.chooseMessageFile({
      count: 1,
      type: 'file',
      extension: ['pdf', 'jpg', 'jpeg', 'png'],
      success: (res) => resolve(res.tempFiles[0]),
      fail: reject
    })
    return
    // #endif

    // #ifdef H5
    const input = document.createElement('input')
    input.type = 'file'
    input.accept = '.pdf,image/*'
    input.onchange = (e) => {
      const f = e.target.files[0]
      if (!f) return reject({ cancel: true })
      resolve({ path: URL.createObjectURL(f), name: f.name, size: f.size })
    }
    input.click()
    // #endif

    // #ifdef APP-PLUS
    uni.chooseFile({
      count: 1,
      success: (res) => resolve({ path: res.tempFilePaths[0], name: res.tempFiles[0].name }),
      fail: reject
    })
    // #endif
  })
}
