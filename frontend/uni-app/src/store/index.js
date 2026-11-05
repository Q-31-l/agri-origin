/**
 * Pinia 装配。
 *
 * 说明：本项目不做 pinia 整体持久化——登录态（token / user / permissions）
 * 已由 utils/auth.js 统一写入 uni storage，store 初始化时再读回，
 * 天然支持三端与冷启动恢复。避免对列表等大对象做无意义持久化。
 */
export function setupStore(pinia) {
  // 预留：需要挂载 pinia 插件时在此处 app.use(pinia)
  return pinia
}
