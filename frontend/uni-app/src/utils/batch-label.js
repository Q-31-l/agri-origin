/**
 * 批次下拉/列表的统一描述。
 *
 * 批次号本身是流水码（AG20260512001），农户根本记不住；只有把"什么作物、
 * 哪个村哪个农场、什么时候播的、大概多少量"一起摆出来，选批次才不会选错。
 * 这段描述同时用在 PC 下拉、移动端选择面板与批次详情卡上，避免多处拼法不一致。
 */
import { BATCH_STATUS } from '@/config/constants'

/** 一行摘要："溪头村 · 阳光家庭农场 · 播种 2026-02-08 · 预估 1200kg" */
export function batchDesc(b) {
  if (!b) return ''
  const parts = []
  if (b.villageName) parts.push(b.villageName)
  if (b.farmName) parts.push(b.farmName)
  if (b.plantingDate) parts.push(`播种 ${b.plantingDate}`)
  if (b.quantity != null && b.quantity !== '') parts.push(`预估 ${b.quantity}${b.unit || ''}`)
  return parts.join(' · ')
}

/** 标题："AG20260512001 · 草莓（红颜）" */
export function batchTitle(b) {
  if (!b) return ''
  const variety = b.variety ? `（${b.variety}）` : ''
  return `${b.batchNo} · ${b.productName || ''}${variety}`
}

export function batchStatusText(status) {
  const st = BATCH_STATUS[status]
  return st ? st.text : status || ''
}

/** PC 下拉选项：两行 + 状态标签 */
export function toBatchOption(b) {
  return {
    value: b.id,
    label: batchTitle(b),
    desc: batchDesc(b),
    tag: batchStatusText(b.status)
  }
}

/** 作业地点：村 + 农场（地块级识别），坐标由定位单独补 */
export function batchPlace(b) {
  if (!b) return ''
  return [b.villageName, b.farmName].filter(Boolean).join(' · ')
}
