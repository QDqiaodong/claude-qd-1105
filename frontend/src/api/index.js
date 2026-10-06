import axios from 'axios'

const http = axios.create({ baseURL: '/api', timeout: 20000 })

http.interceptors.response.use(
  (response) => response,
  (error) => {
    const message = error?.response?.data?.message || error?.message || '请求失败'
    return Promise.reject(new Error(message))
  }
)

async function call(promise) {
  const response = await promise
  const body = response.data
  if (!body || body.ok === false) {
    throw new Error((body && body.message) || '请求失败')
  }
  return body.data
}

const params = (o) => ({ params: o })

export default {
  // ---------------- 图书（行内编辑） ----------------
  listBooks: (status, keyword) => call(http.get('/books', params(
    status ? { status, ...(keyword ? { keyword } : {}) } : (keyword ? { keyword } : {})))),
  createBook: (d) => call(http.post('/books', d)),
  updateBook: (id, d) => call(http.put(`/books/${id}`, d)),

  // ---------------- 供应商（卡片网格） ----------------
  listSuppliers: (keyword, status) => call(http.get('/suppliers', params({
    ...(keyword ? { keyword } : {}),
    ...(status ? { status } : {})
  }))),
  createSupplier: (d) => call(http.post('/suppliers', d)),
  updateSupplier: (id, d) => call(http.put(`/suppliers/${id}`, d)),
  changeSupplierStatus: (id, status, reason) =>
    call(http.post(`/suppliers/${id}/status`, { status, reason })),
  supplierStatusLogs: (id) => call(http.get(`/suppliers/${id}/status-logs`)),

  // ---------------- 进货单 ----------------
  listPurchases: (status) => call(http.get('/purchases', params(status ? { status } : {}))),
  getPurchase: (id) => call(http.get(`/purchases/${id}`)),
  createPurchase: (d) => call(http.post('/purchases', d)),
  updatePurchase: (id, d) => call(http.put(`/purchases/${id}`, d)),
  stockInPurchase: (id) => call(http.post(`/purchases/${id}/stock-in`)),
  voidPurchase: (id) => call(http.post(`/purchases/${id}/void`)),

  // ---------------- 销售单 ----------------
  listSales: (status) => call(http.get('/sales', params(status ? { status } : {}))),
  getSale: (id) => call(http.get(`/sales/${id}`)),
  createSale: (d) => call(http.post('/sales', d)),
  updateSale: (id, d) => call(http.put(`/sales/${id}`, d)),
  checkoutSale: (id) => call(http.post(`/sales/${id}/checkout`)),
  refundSale: (id) => call(http.post(`/sales/${id}/refund`))
}
