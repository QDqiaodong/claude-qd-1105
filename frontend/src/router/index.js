import { createRouter, createWebHashHistory } from 'vue-router'

// 注意：main.js 里没有 app.use(router)，路由实例由 App.vue 直接驱动，
// 所以这里全部用静态 import —— <component :is> 拿到的是组件对象本身。
import BookView from '../views/BookView.vue'
import SupplierView from '../views/SupplierView.vue'
import PurchaseView from '../views/PurchaseView.vue'
import SaleView from '../views/SaleView.vue'

const routes = [
  { path: '/', redirect: '/books' },
  { path: '/books', name: 'books', component: BookView },
  { path: '/suppliers', name: 'suppliers', component: SupplierView },
  { path: '/purchases', name: 'purchases', component: PurchaseView },
  { path: '/sales', name: 'sales', component: SaleView },
  { path: '/:pathMatch(.*)*', redirect: '/books' }
]

export default createRouter({
  history: createWebHashHistory(),
  routes
})
