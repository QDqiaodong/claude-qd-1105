<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const emit = defineEmits(['changed'])

const rows = ref([])
const keyword = ref('')
const statusFilter = ref('')
const loading = ref(false)

const STATUS_OPTIONS = ['在售', '缺货', '下架']

const showNew = ref(false)
const newRow = ref({ isbn: '', title: '', author: '', price: 0, stock: 0, status: '在售' })

async function load() {
  loading.value = true
  try {
    rows.value = await api.listBooks(statusFilter.value || '', keyword.value || '')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function saveRow(row) {
  try {
    await api.updateBook(row.id, {
      isbn: row.isbn,
      title: row.title,
      author: row.author,
      price: row.price,
      stock: row.stock,
      status: row.status
    })
    ElMessage.success('已保存')
    await load()
    emit('changed')
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function openNew() {
  newRow.value = { isbn: '', title: '', author: '', price: 0, stock: 0, status: '在售' }
  showNew.value = true
}

async function submitNew() {
  try {
    await api.createBook({ ...newRow.value })
    ElMessage.success('新增成功')
    showNew.value = false
    await load()
    emit('changed')
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function statusType(s) {
  if (s === '在售') return 'success'
  if (s === '缺货') return 'warning'
  return 'info'
}

onMounted(load)
</script>

<template>
  <div>
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="搜索书名 / 作者 / ISBN" clearable style="width: 240px"
        @keyup.enter="load" @clear="load" />
      <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 140px" @change="load">
        <el-option v-for="s in STATUS_OPTIONS" :key="s" :label="s" :value="s" />
      </el-select>
      <el-button type="primary" @click="load">查询</el-button>
      <el-button @click="openNew">新增图书</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" border stripe style="margin-top: 12px">
      <el-table-column prop="isbn" label="ISBN" min-width="150">
        <template #default="{ row }">
          <el-input v-model="row.isbn" size="small" />
        </template>
      </el-table-column>
      <el-table-column prop="title" label="书名" min-width="160">
        <template #default="{ row }">
          <el-input v-model="row.title" size="small" />
        </template>
      </el-table-column>
      <el-table-column prop="author" label="作者" min-width="120">
        <template #default="{ row }">
          <el-input v-model="row.author" size="small" />
        </template>
      </el-table-column>
      <el-table-column prop="price" label="定价" width="130">
        <template #default="{ row }">
          <el-input-number v-model="row.price" :min="0" :precision="2" :step="1" size="small" controls-position="right" style="width: 110px" />
        </template>
      </el-table-column>
      <el-table-column prop="stock" label="库存" width="130">
        <template #default="{ row }">
          <el-input-number v-model="row.stock" :min="0" :step="1" size="small" controls-position="right" style="width: 110px" />
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="120">
        <template #default="{ row }">
          <el-select v-model="row.status" size="small" style="width: 90px">
            <el-option v-for="s in STATUS_OPTIONS" :key="s" :label="s" :value="s" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="saveRow(row)">保存</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 行内新增表单（非弹窗） -->
    <el-card v-if="showNew" class="new-card" shadow="never">
      <template #header>新增图书</template>
      <div class="new-form">
        <el-input v-model="newRow.isbn" placeholder="ISBN" style="width: 160px" />
        <el-input v-model="newRow.title" placeholder="书名" style="width: 160px" />
        <el-input v-model="newRow.author" placeholder="作者" style="width: 120px" />
        <el-input-number v-model="newRow.price" :min="0" :precision="2" placeholder="定价" style="width: 120px" controls-position="right" />
        <el-input-number v-model="newRow.stock" :min="0" placeholder="库存" style="width: 110px" controls-position="right" />
        <el-button type="primary" @click="submitNew">确定</el-button>
        <el-button @click="showNew = false">取消</el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.toolbar { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.new-card { margin-top: 16px; }
.new-form { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
</style>
