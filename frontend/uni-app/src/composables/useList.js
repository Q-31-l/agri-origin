import { ref } from 'vue'

/**
 * 列表页通用逻辑：分页、下拉刷新、上拉加载、筛选
 * @param {(params) => Promise} fetcher 接收 {page,size,...filters}，返回后端 Page 结构
 */
export function useList(fetcher, options = {}) {
  const {
    pageSize = 10,
    immediate = true,
    filters = () => ({}),
    transform = (rows) => rows
  } = options

  const list = ref([])
  const page = ref(1)
  const total = ref(0)
  const loading = ref(false)
  const status = ref('more') // more | loading | noMore | error
  const firstLoaded = ref(false)

  async function load(reset = false) {
    if (loading.value) return
    if (reset) {
      page.value = 1
      status.value = 'more'
    }
    if (status.value === 'noMore' && !reset) return

    loading.value = true
    status.value = 'loading'
    try {
      const res = await fetcher({ page: page.value, size: pageSize, ...filters() })
      const data = res.data || {}
      const rows = transform(data.records || [])
      total.value = data.total || 0

      if (reset) list.value = rows
      else list.value = list.value.concat(rows)

      const loaded = list.value.length
      if (loaded >= total.value || rows.length === 0) {
        status.value = 'noMore'
      } else {
        status.value = 'more'
      }
      firstLoaded.value = true
    } catch (e) {
      status.value = 'error'
    } finally {
      loading.value = false
      uni.stopPullDownRefresh()
    }
  }

  function reload() {
    return load(true)
  }

  function loadMore() {
    if (loading.value || status.value === 'noMore') return
    page.value += 1
    load(false)
  }

  function refresh() {
    return load(true)
  }

  if (immediate) load(true)

  return { list, page, total, loading, status, firstLoaded, load, reload, loadMore, refresh }
}
