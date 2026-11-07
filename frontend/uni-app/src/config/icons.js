/**
 * 统一图标库（24×24 线性描边，stroke 用 currentColor 跟随文字色）
 *
 * 为什么不用字符图标：▤ ▢ ❀ ✿ ⛓ ⌕ 这些 Unicode 符号在 Windows / iOS / Android
 * 的字体里字形完全不同，同一个"链上存证"在部分设备上会渲染成两个叉，
 * 在另一些设备上又是一条链——同一个系统在不同机器上长得不一样。
 *
 * 这里存的是 SVG 内部标记字符串，由 pc-icon.vue 用 v-html 塞进 <svg>。
 * 只存 path/rect/circle 这类叶子节点，不带 <svg> 外壳，也不带颜色值。
 */
export const ICONS = {
  /* ---- 业务 ---- */
  dashboard:
    '<rect x="3.5" y="3.5" width="7" height="7" rx="1.6"/><rect x="13.5" y="3.5" width="7" height="4.6" rx="1.4"/>' +
    '<rect x="3.5" y="13.6" width="7" height="7" rx="1.6"/><rect x="13.5" y="11.1" width="7" height="9.4" rx="1.6"/>',

  batch:
    '<path d="M12 3 3 7.5l9 4.5 9-4.5L12 3Z"/><path d="m3 12 9 4.5 9-4.5"/><path d="m3 16.5 9 4.5 9-4.5"/>',

  planting:
    '<path d="M12 21.5V10.5"/><path d="M12 11C12 6.8 8.8 4 4.2 4c0 5.2 3.2 7 7.8 7Z"/>' +
    '<path d="M12 13.4c0-3.9 2.8-6.4 7.4-6.4 0 4.6-2.8 6.4-7.4 6.4Z"/>',

  harvest:
    '<path d="M3.6 9h16.8l-1.7 9.6a2 2 0 0 1-2 1.7H7.3a2 2 0 0 1-2-1.7L3.6 9Z"/>' +
    '<path d="M8.8 9V6.2a3.2 3.2 0 0 1 6.4 0V9"/>',

  quality:
    '<path d="M12 3 5 6v5.6c0 4.6 3 8 7 9.4 4-1.4 7-4.8 7-9.4V6l-7-3Z"/><path d="m9.2 12.1 2.1 2.2 4.1-4.1"/>',

  logistics:
    '<path d="M2.8 6.8h10.4v9.6H2.8z"/><path d="M13.2 10.2h3.9l3.1 3.1v3.1h-7z"/>' +
    '<circle cx="6.8" cy="18.1" r="1.9"/><circle cx="17.1" cy="18.1" r="1.9"/>',

  sales:
    '<path d="M20.6 12.6 12 21.2l-8.6-8.6V3.4H12l8.6 8.6.1.6Z"/><circle cx="7.6" cy="7.6" r="1.5"/>',

  abnormal:
    '<path d="M12 3.4 22.2 20.4H1.8L12 3.4Z"/><path d="M12 9.4v5.2"/>' +
    '<circle cx="12" cy="17.4" r=".95" fill="currentColor" stroke="none"/>',

  chain:
    '<path d="m9.4 14.6 5.2-5.2"/><path d="M7.4 11 5 13.4a3.6 3.6 0 0 0 5.1 5.1l2.4-2.4"/>' +
    '<path d="M16.6 13 19 10.6a3.6 3.6 0 0 0-5.1-5.1L11.5 8"/>',

  trace: '<circle cx="10.8" cy="10.8" r="6.6"/><path d="m15.6 15.6 5 5"/>',

  /* ---- 系统 ---- */
  user:
    '<circle cx="12" cy="8" r="3.7"/><path d="M4.4 20.2c.7-3.7 3.7-5.8 7.6-5.8s6.9 2.1 7.6 5.8"/>',

  farm:
    '<path d="M3.4 10.6 12 3.4l8.6 7.2"/><path d="M5.6 9.6V20.2h12.8V9.6"/><path d="M9.8 20.2v-5.4h4.4v5.4"/>',

  product:
    '<path d="M12 3 3.4 7.6v8.8L12 21l8.6-4.6V7.6L12 3Z"/><path d="M3.4 7.6 12 12.2l8.6-4.6"/><path d="M12 12.2V21"/>',

  log: '<path d="M3.8 6.4h16.4M3.8 12h16.4M3.8 17.6h10.4"/>',

  settings:
    '<circle cx="12" cy="12" r="3.1"/><path d="M12 2.6v3M12 18.4v3M2.6 12h3M18.4 12h3"/>' +
    '<path d="m5.1 5.1 2.1 2.1M16.8 16.8l2.1 2.1M18.9 5.1l-2.1 2.1M7.2 16.8l-2.1 2.1"/>',

  lock:
    '<rect x="4.4" y="10" width="15.2" height="10.6" rx="2.2"/><path d="M8 10V7.4a4 4 0 0 1 8 0V10"/>',

  /* ---- 操作 ---- */
  plus: '<path d="M12 4.8v14.4M4.8 12h14.4"/>',
  check: '<path d="m4.8 12.6 4.6 4.6L19.2 7.4"/>',
  close: '<path d="M6 6l12 12M18 6 6 18"/>',
  refresh: '<path d="M20.2 11.6A8.4 8.4 0 1 0 18.5 16"/><path d="M20.2 4.6v7h-7"/>',
  search: '<circle cx="10.8" cy="10.8" r="6.6"/><path d="m15.6 15.6 5 5"/>',
  scan:
    '<path d="M4 9.2V6.4A2.4 2.4 0 0 1 6.4 4H9"/><path d="M15 4h2.6A2.4 2.4 0 0 1 20 6.4v2.8"/>' +
    '<path d="M20 14.8v2.8a2.4 2.4 0 0 1-2.4 2.4H15"/><path d="M9 20H6.4A2.4 2.4 0 0 1 4 17.6v-2.8"/>' +
    '<path d="M3.6 12h16.8"/>',

  location:
    '<path d="M12 21.2s7.1-4.5 7.1-10.2A7.1 7.1 0 1 0 4.9 11c0 5.7 7.1 10.2 7.1 10.2Z"/>' +
    '<circle cx="12" cy="10.8" r="2.6"/>',

  clock: '<circle cx="12" cy="12" r="8.6"/><path d="M12 7.2V12l3.2 2"/>',

  camera:
    '<path d="M3.6 8.2h3.2l1.6-2.2h7.2l1.6 2.2h3.2v11.2H3.6z"/><circle cx="12" cy="13.4" r="3.5"/>',

  upload:
    '<path d="M12 16.4V3.8"/><path d="m7.8 8 4.2-4.2L16.2 8"/><path d="M3.8 16.2v3a1.4 1.4 0 0 0 1.4 1.4h13.6a1.4 1.4 0 0 0 1.4-1.4v-3"/>',

  image:
    '<rect x="3.4" y="4.6" width="17.2" height="14.8" rx="2.2"/><circle cx="8.6" cy="9.6" r="1.7"/>' +
    '<path d="m4.4 17.4 4.6-4.4 3.2 3 2.8-2.6 4.6 4.4"/>',

  video:
    '<rect x="2.8" y="6.2" width="12.6" height="11.6" rx="2.2"/><path d="m15.4 11 5.8-3.4v8.8L15.4 13"/>',

  bell:
    '<path d="M6 10.2a6 6 0 0 1 12 0c0 5 2 6.2 2 6.2H4s2-1.2 2-6.2Z"/><path d="M9.8 19.4a2.2 2.2 0 0 0 4.4 0"/>',

  /* 微信登录：双气泡 + 四点，象征对话 */
  wechat:
    '<path d="M10.4 3.9c-4 0-7.2 2.6-7.2 5.9 0 1.9 1.1 3.5 2.9 4.6l-.8 2.4 2.7-1.4c.8.2 1.6.3 2.4.3"/>' +
    '<path d="M14.6 9.3c3.3 0 6 2.2 6 4.9 0 1.6-.9 3-2.4 3.9l.7 2-2.2-1.2c-.7.2-1.4.3-2.1.3-3.3 0-6-2.2-6-4.9s2.7-5 6-5Z"/>' +
    '<circle cx="7.7" cy="8.5" r=".95" fill="currentColor" stroke="none"/>' +
    '<circle cx="12.5" cy="8.5" r=".95" fill="currentColor" stroke="none"/>' +
    '<circle cx="12.3" cy="13.7" r=".82" fill="currentColor" stroke="none"/>' +
    '<circle cx="16.5" cy="13.7" r=".82" fill="currentColor" stroke="none"/>',

  logout:
    '<path d="M14 3.8h4.6A1.6 1.6 0 0 1 20.2 5.4v13.2a1.6 1.6 0 0 1-1.6 1.6H14"/>' +
    '<path d="M9.6 8.2 5.6 12l4 3.8"/><path d="M5.6 12h9.2"/>',

  arrowRight: '<path d="M4.6 12h14.8"/><path d="m13.8 6.4 5.6 5.6-5.6 5.6"/>',
  arrowDown: '<path d="M12 4.6v14.8"/><path d="m6.4 13.8 5.6 5.6 5.6-5.6"/>',
  chevronRight: '<path d="m9.4 5.6 6.4 6.4-6.4 6.4"/>',
  chevronDown: '<path d="m5.6 9.4 6.4 6.4 6.4-6.4"/>',

  empty: '<circle cx="12" cy="12" r="8.6"/><path d="M8.4 12h7.2"/>'
}

/** 取图标内部标记；未知名称返回一个占位点，避免渲染出空白 */
export function iconMarkup(name) {
  return ICONS[name] || '<circle cx="12" cy="12" r="1.6" fill="currentColor" stroke="none"/>'
}
