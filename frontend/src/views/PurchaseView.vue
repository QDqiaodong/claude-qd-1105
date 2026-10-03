<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const emit = defineEmits(['changed'])

const purchases = ref([])
const selectedId = ref(null)
const detail = ref(null)
const loading = ref(false)

const books = ref([])
const suppliers = ref([])
const dialogVisible = ref(false)
const saving = ref(false)
const form = ref({ supplierId: null, items: [] })

const STATUS_TAG = { '草稿': 'info', '已入库': 'success', '已作废': 'danger' }

async function load() {
  loading.value = true
  try {
    purchases.value = await api.listPurchases()
    if (!selectedId.value && purchases.value.length) {
      selectedId.value = purchases.value[0].id
    }
    if (selectedId.value) {
      detail.value = await api.getPurchase(selectedId.value)
    } else {
      detail.value = null
    }
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function selectPurchase(id) {
  selectedId.value = id
  try {
    detail.value = await api.getPurchase(id)
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function bookTitle(id) {
  const b = books.value.find((x) => x.id === id)
  return b ? `${b.title}（库存${b.stock}）` : `#${id}`
}

async function openNew() {
  books.value = await api.listBooks()
  suppliers.value = await api.listSuppliers()
  form.value = { supplierId: null, items: [] }
  dialogVisible.value = true
}

function addItem() {
  form.value.items.push({ bookId: null, qty: 1, cost: 0 })
}

function removeItem(i) {
  form.value.items.splice(i, 1)
}

async function submitNew() {
  saving.value = true
  try {
    await api.createPurchase({
      supplierId: form.value.supplierId,
      items: form.value.items.map((it) => ({ bookId: it.bookId, qty: it.qty, cost: it.cost }))
    })
    ElMessage.success('进货单已创建（草稿）')
    dialogVisible.value = false
    await load()
    emit('changed')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

async function stockIn() {
  try {
    await api.stockInPurchase(selectedId.value)
    ElMessage.success('已入库，库存已增加')
    await load()
    emit('changed')
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function voidIt() {
  try {
    await api.voidPurchase(selectedId.value)
    ElMessage.success('已作废')
    await load()
    emit('changed')
  } catch (e) {
    ElMessage.error(e.message)
  }
}

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
          <div class="row-sub">供应商 #{{ p.supplierId }} · 总额 ¥{{ (p.total || 0).toFixed(2) }}</div>
        </div>
        <el-empty v-if="!purchases.length" description="暂无进货单" :image-size="60" />
      </div>

      <!-- 右侧明细 -->
      <div class="detail">
        <template v-if="detail">
          <div class="detail-head">
            <span>进货单 #{{ detail.purchase.id }}</span>
            <el-tag :type="STATUS_TAG[detail.purchase.status]" size="small">{{ detail.purchase.status }}</el-tag>
            <span class="muted">供应商 #{{ detail.purchase.supplierId }}</span>
            <span class="muted">总额 ¥{{ (detail.purchase.total || 0).toFixed(2) }}</span>
          </div>
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
              :disabled="detail.purchase.status !== '草稿'"
              @click="stockIn"
            >入库</el-button>
            <el-button
              type="danger"
              :disabled="detail.purchase.status === '已作废'"
              @click="voidIt"
            >作废</el-button>
          </div>
        </template>
        <el-empty v-else description="请选择左侧进货单" />
      </div>
    </div>

    <!-- 新建弹窗 -->
    <el-dialog v-model="dialogVisible" title="新建进货单（草稿）" width="640px">
      <el-form label-width="80px">
        <el-form-item label="供应商">
          <el-select v-model="form.supplierId" placeholder="选择供应商" style="width: 100%">
            <el-option v-for="s in suppliers" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="明细">
          <div class="items">
            <div v-for="(it, i) in form.items" :key="i" class="item-row">
              <el-select v-model="it.bookId" placeholder="图书" style="width: 220px" filterable>
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
        <el-button type="primary" :loading="saving" @click="submitNew">创建</el-button>
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
.detail { flex: 1; min-width: 0; background: #fff; border: 1px solid var(--line); border-radius: 8px; padding: 14px 16px; }
.detail-head { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; font-weight: 600; }
.muted { color: var(--muted); font-weight: 400; font-size: 13px; }
.actions { margin-top: 14px; display: flex; gap: 10px; }
.items { display: flex; flex-direction: column; gap: 8px; }
.item-row { display: flex; gap: 8px; align-items: center; }
</style>
