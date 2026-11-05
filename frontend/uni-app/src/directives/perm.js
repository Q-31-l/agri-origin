import { useUserStore } from '@/store/user'

/** v-perm="'batch:create'"：无权限时移除该节点 */
export default {
  mounted(el, binding) {
    const store = useUserStore()
    if (binding.value && !store.hasPerm(binding.value)) {
      el.parentNode && el.parentNode.removeChild(el)
    }
  }
}
