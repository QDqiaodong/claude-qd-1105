<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

const emit = defineEmits(['changed'])

const purchases = ref([])
const selectedId = ref(null)
const detail = ref(null)
const loading = ref(false)

const books = ref([])
const suppliers = ref([])

// 新建 / 编辑共用一个弹窗
const dialogVisible = ref(false)
const dialogMode = ref('create') // create / edit
const saving = ref(false)
const form = ref({ supplierId: null, items: [] })

const STATUS_TAG = { '草稿': 'info', '已入库': 'success', '已作废': 'danger' }

function supplierOf(supplierId) {
  return suppliers.value.find((x) => x.id === supplierId) || null
}

function supplierName(supplierId) {
  const s = supplierOf(supplierId)
  return s ? s.name : `供应商 #${supplierId}`
}

function isSupplierInactive(supplierId) {
  const s = supplierOf(supplierId)
  // 查不到供应商记录时不谎报状态，交给后端按不存在处理
  return s ? s.status === '停用' : false
}

function bookTitle(id) {
  const b = books.value.find((x) => x.id === id)
  return b ? `${b.title}（库存${b.stock}）` : `#${id}`
}

// 仅加载右侧明细（操作后局部刷新即可）
async function refreshDetail() {
  if (selectedId.value) {
    detail.value = await api.getPurchase(selectedId.value)
  }
}

// 全量刷新：单据、供应商状态、图书库存都以服务端为准
async function refreshAll() {
  const [ps, ss, bs] = await Promise.all([
    api.listPurchases(),
    api.listSuppliers(),
    api.listBooks()
  ])
  purchases.value = ps
  suppliers.value = ss
  books.value = bs
  if (selectedId.value) {
    detail.value = await api.getPurchase(selectedId.value)
  } else if (purchases.value.length) {
    selectedId.value = purchases.value[0].id
    detail.value = await api.getPurchase(selectedId.value)
  } else {
    detail.value = null
  }
}

async function load() {
  loading.value = true
  try {
    if (!selectedId.value) {
      const ps = await api.listPurchases()
      purchases.value = ps
      if (ps.length) {
        selectedId.value = ps[0].id
      }
    }
    await refreshAll()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function selectPurchase(id) {
  selectedId.value = id
  try {
    await refreshDetail()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function openNew() {
  dialogMode.value = 'create'
  // 打开选择器前重新拉一次供应商，停用/启用状态以此刻服务端为准
  const [ss, bs] = await Promise.all([api.listSuppliers(), api.listBooks()])
  suppliers.value = ss
  books.value = bs
  form.value = { supplierId: null, items: [] }
  dialogVisible.value = true
}

async function openEdit() {
  dialogMode.value = 'edit'
  const [ss, bs] = await Promise.all([api.listSuppliers(), api.listBooks()])
  suppliers.value = ss
  books.value = bs
  form.value = {
    supplierId: detail.value.purchase.supplierId,
    items: detail.value.items.map((it) => ({ bookId: it.bookId, qty: it.qty, cost: Number(it.cost) }))
  }
  dialogVisible.value = true
}

function addItem() {
  form.value.items.push({ bookId: null, qty: 1, cost: 0 })
}

function removeItem(i) {
  form.value.items.splice(i, 1)
}

// 下拉选项的文案：停用供应商明示状态与原因
function supplierOptionLabel(s) {
  if (s.status === '停用') {
    const reason = s.inactiveReason || '未填写原因'
    return `${s.name}（已停用：${reason}）`
  }
  return s.name
}

function validateForm() {
  if (!form.value.supplierId) {
    ElMessage.warning('请选择供应商')
    return false
  }
  if (dialogMode.value === 'create') {
    const s = supplierOf(form.value.supplierId)
    if (s && s.status === '停用') {
      ElMessage.warning('已停用的供应商不能用于新建采购草稿')
      return false
    }
  }
  if (!form.value.items.length) {
    ElMessage.warning('请至少添加一条进货明细')
    return false
  }
  for (const it of form.value.items) {
    if (!it.bookId) {
      ElMessage.warning('每条明细都要选择图书')
      return false
    }
    if (!it.qty || it.qty <= 0) {
      ElMessage.warning('进货数量必须大于 0')
      return false
    }
    if (it.cost === null || it.cost === undefined || it.cost <= 0) {
      ElMessage.warning('进价必须大于 0')
      return false
    }
  }
  return true
}

async function submitForm() {
  if (!validateForm()) return
  const payload = {
    supplierId: form.value.supplierId,
    items: form.value.items.map((it) => ({ bookId: it.bookId, qty: it.qty, cost: it.cost }))
  }
  saving.value = true
  try {
    if (dialogMode.value === 'create') {
      await api.createPurchase(payload)
      ElMessage.success('进货单已创建（草稿）')
    } else {
      await api.updatePurchase(detail.value.purchase.id, payload)
      ElMessage.success('草稿已保存')
    }
    dialogVisible.value = false
    await refreshAll()
    emit('changed')
  } catch (e) {
    // 409 并发冲突 / 停用拦截：服务端已回滚，刷新后用户可安全重试
    ElMessage.error(e.message)
    await refreshAll()
  } finally {
    saving.value = false
  }
}

async function stockIn() {
  const p = detail.value.purchase
  // 前端先拦一道（后端入库前会再强制校验，状态以后端为准）
  if (isSupplierInactive(p.supplierId)) {
    ElMessage.error('供应商已停用，本单不能入库；重新启用后可继续操作')
    await refreshAll()
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认把进货单 #${p.id} 送入库？库存将随明细增加；若供应商在此期间被停用，入库会被整单拒绝。`,
      '入库确认',
      { type: 'warning', confirmButtonText: '入库', cancelButtonText: '取消' }
    )
  } catch {
    return // 用户取消
  }
  try {
    await api.stockInPurchase(p.id)
    ElMessage.success('已入库，库存已增加')
    await refreshAll()
    emit('changed')
  } catch (e) {
    // 关键：失败后整单可能因并发被拒，必须刷新拿到最新供应商状态/采购单状态，避免界面误判后重复点击
    ElMessage.error(e.message)
    await refreshAll()
  }
}

async function voidIt() {
  try {
    await api.voidPurchase(detail.value.purchase.id)
    ElMessage.success('已作废')
    await refreshAll()
    emit('changed')
  } catch (e) {
    ElMessage.error(e.message)
    await refreshAll()
  }
}

const currentSupplierInactive = () =>
  detail.value && detail.value.purchase.status === '草稿' &&
  isSupplierInactive(detail.value.purchase.supplierId)

const currentSupplier = () =>
  detail.value ? supplierOf(detail.value.purchase.supplierId) : null

onMounted(load)
</script>

<template>
  <div>
    <div class="toolbar">
      <h3 style="margin:0">进货单</h3>
      <el-button type="primary" @click="openNew">新建进货单</el-button>
    </div>

    <div class="layout" v-loading="loading">
      <!-- 左侧列表 -->
      <div class="list">
        <div
          v-for="p in purchases"
          :key="p.id"
          class="row"
          :class="{ on: p.id === selectedId }"
          @click="selectPurchase(p.id)"
        >
          <div class="row-top">
            <span class="pid">进货单 #{{ p.id }}</span>
            <el-tag :type="STATUS_TAG[p.status]" size="small">{{ p.status }}</el-tag>
          </div>
          <div class="row-sub">
            {{ supplierName(p.supplierId) }} · 总额 ¥{{ (p.total || 0).toFixed(2) }}
          </div>
          <!-- 闭环①：列表上直接标出“供应商已停用、不能入库”的草稿 -->
          <div v-if="p.status === '草稿' && isSupplierInactive(p.supplierId)" class="row-warn">
            ⚠ 供应商已停用，暂不能入库
          </div>
        </div>
        <el-empty v-if="!purchases.length" description="暂无进货单" :image-size="60" />
      </div>

      <!-- 右侧明细 -->
      <div class="detail">
        <template v-if="detail">
          <div class="detail-head">
            <span>进货单 #{{ detail.purchase.id }}</span>
            <el-tag :type="STATUS_TAG[detail.purchase.status]" size="small">{{ detail.purchase.status }}</el-tag>
            <span class="muted">
              供应商：{{ supplierName(detail.purchase.supplierId) }}
            </span>
            <el-tag
              v-if="currentSupplier()"
              :type="currentSupplier().status === '停用' ? 'danger' : 'success'"
              size="small"
            >{{ currentSupplier().status }}</el-tag>
            <span class="muted">总额 ¥{{ (detail.purchase.total || 0).toFixed(2) }}</span>
          </div>

          <!-- 闭环②：草稿挂在停用供应商上，原因与后果直接显示在单据详情 -->
          <el-alert
            v-if="currentSupplierInactive()"
            class="block-alert"
            type="error"
            :closable="false"
            show-icon
            :title="`供应商已停用（停用原因：${currentSupplier().inactiveReason || '未填写原因'}）`"
            description="本单保留原供应商关系，可继续查看或补充明细，但不能入库；待供应商重新启用后即可正常入库。"
          />

          <el-table :data="detail.items" border size="small" style="margin-top:10px">
            <el-table-column label="图书" min-width="180">
              <template #default="{ row }">{{ bookTitle(row.bookId) }}</template>
            </el-table-column>
            <el-table-column prop="qty" label="数量" width="80" />
            <el-table-column label="进价" width="100">
              <template #default="{ row }">¥{{ (row.cost || 0).toFixed(2) }}</template>
            </el-table-column>
            <el-table-column label="小计" width="110">
              <template #default="{ row }">¥{{ ((row.cost || 0) * (row.qty || 0)).toFixed(2) }}</template>
            </el-table-column>
          </el-table>
          <div class="actions">
            <el-button
              type="success"
              :disabled="detail.purchase.status !== '草稿' || currentSupplierInactive()"
              @click="stockIn"
            >入库</el-button>
            <el-button
              v-if="detail.purchase.status === '草稿'"
              type="primary"
              plain
              @click="openEdit"
            >补充/编辑明细</el-button>
            <el-button
              type="danger"
              :disabled="detail.purchase.status === '已作废'"
              @click="voidIt"
            >作废</el-button>
            <span v-if="currentSupplierInactive()" class="action-tip">
              供应商停用中，入库按钮已锁定；重新启用后自动恢复
            </span>
          </div>
        </template>
        <el-empty v-else description="请选择左侧进货单" />
      </div>
    </div>

    <!-- 新建 / 编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新建进货单（草稿）' : `编辑草稿 #${detail ? detail.purchase.id : ''}`"
      width="680px"
    >
      <el-alert
        v-if="dialogMode === 'edit' && currentSupplierInactive()"
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 10px"
        title="当前供应商已停用：明细可照常补充保存，但本单在供应商重新启用前不能入库；更换供应商只能选择其他启用供应商。"
      />
      <el-form label-width="80px">
        <el-form-item label="供应商">
          <el-select v-model="form.supplierId" placeholder="选择供应商" style="width: 100%">
            <el-option
              v-for="s in suppliers"
              :key="s.id"
              :label="supplierOptionLabel(s)"
              :value="s.id"
              :disabled="s.status !== '启用' && (dialogMode === 'create' || s.id !== form.supplierId)"
            >
              <span :class="{ 'opt-off': s.status === '停用' }">{{ s.name }}</span>
              <el-tag v-if="s.status === '停用'" type="danger" size="small" style="margin-left:8px">
                已停用{{ s.inactiveReason ? '：' + s.inactiveReason : '' }}
              </el-tag>
              <el-tag v-else type="success" size="small" style="margin-left:8px">启用</el-tag>
            </el-option>
          </el-select>
          <div v-if="dialogMode === 'create'" class="field-tip">仅可选择“启用”供应商；停用供应商不可用于新建采购草稿。</div>
        </el-form-item>
        <el-form-item label="明细">
          <div class="items">
            <div v-for="(it, i) in form.items" :key="i" class="item-row">
              <el-select v-model="it.bookId" placeholder="图书" style="width: 240px" filterable>
                <el-option v-for="b in books" :key="b.id" :label="`${b.title}（库存${b.stock}）`" :value="b.id" />
              </el-select>
              <el-input-number v-model="it.qty" :min="1" controls-position="right" style="width: 110px" />
              <el-input-number v-model="it.cost" :min="0" :precision="2" controls-position="right" style="width: 130px" />
              <el-button link type="danger" @click="removeItem(i)">删</el-button>
            </div>
            <el-button link type="primary" @click="addItem">+ 添加明细</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">
          {{ dialogMode === 'create' ? '创建' : '保存' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 14px; margin-bottom: 14px; }
.layout { display: flex; gap: 16px; align-items: flex-start; }
.list { width: 280px; flex-shrink: 0; display: flex; flex-direction: column; gap: 8px; }
.row { border: 1px solid var(--line); border-radius: 8px; padding: 10px 12px; cursor: pointer; background: #fff; }
.row:hover { border-color: var(--primary-soft); }
.row.on { border-color: var(--primary); background: var(--primary-wash); }
.row-top { display: flex; justify-content: space-between; align-items: center; }
.pid { font-weight: 600; }
.row-sub { color: var(--muted); font-size: 12px; margin-top: 4px; }
.row-warn { color: #c45656; font-size: 12px; margin-top: 4px; }
.detail { flex: 1; min-width: 0; background: #fff; border: 1px solid var(--line); border-radius: 8px; padding: 14px 16px; }
.detail-head { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; font-weight: 600; }
.muted { color: var(--muted); font-weight: 400; font-size: 13px; }
.block-alert { margin-top: 10px; }
.actions { margin-top: 14px; display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.action-tip { color: #c45656; font-size: 12px; }
.field-tip { color: var(--muted); font-size: 12px; margin-top: 4px; }
.items { display: flex; flex-direction: column; gap: 8px; width: 100%; }
.item-row { display: flex; gap: 8px; align-items: center; }
.opt-off { color: var(--muted); }
</style>
