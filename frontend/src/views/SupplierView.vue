<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const emit = defineEmits(['changed'])

const suppliers = ref([])
const keyword = ref('')
const loading = ref(false)
const editingId = ref(null)
const draft = ref({ name: '', contact: '', phone: '' })

const showNew = ref(false)
const newRow = ref({ name: '', contact: '', phone: '' })

async function load() {
  loading.value = true
  try {
    suppliers.value = await api.listSuppliers(keyword.value || '')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function startEdit(s) {
  editingId.value = s.id
  draft.value = { name: s.name, contact: s.contact, phone: s.phone }
}

async function saveEdit(s) {
  try {
    await api.updateSupplier(s.id, { ...draft.value })
    ElMessage.success('已保存')
    editingId.value = null
    await load()
    emit('changed')
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function cancelEdit() {
  editingId.value = null
}

function openNew() {
  newRow.value = { name: '', contact: '', phone: '' }
  showNew.value = true
}

async function submitNew() {
  try {
    await api.createSupplier({ ...newRow.value })
    ElMessage.success('新增成功')
    showNew.value = false
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
      <el-input v-model="keyword" placeholder="搜索供应商名" clearable style="width: 220px"
        @keyup.enter="load" @clear="load" />
      <el-button type="primary" @click="load">查询</el-button>
      <el-button @click="openNew">新增供应商</el-button>
    </div>

    <div v-loading="loading" class="grid">
      <el-card v-for="s in suppliers" :key="s.id" class="card" shadow="hover">
        <template #header>
          <div class="card-head">
            <span class="name">{{ s.name }}</span>
            <span class="sid">#{{ s.id }}</span>
          </div>
        </template>

        <div v-if="editingId === s.id" class="form">
          <el-input v-model="draft.name" size="small" placeholder="供应商名" />
          <el-input v-model="draft.contact" size="small" placeholder="联系人" />
          <el-input v-model="draft.phone" size="small" placeholder="手机" />
          <div class="ops">
            <el-button type="primary" size="small" @click="saveEdit(s)">保存</el-button>
            <el-button size="small" @click="cancelEdit">取消</el-button>
          </div>
        </div>
        <div v-else class="info">
          <div><span class="k">联系人</span>{{ s.contact || '—' }}</div>
          <div><span class="k">手机</span>{{ s.phone || '—' }}</div>
          <el-button link type="primary" size="small" @click="startEdit(s)">编辑</el-button>
        </div>
      </el-card>
    </div>

    <el-card v-if="showNew" class="new-card" shadow="never">
      <template #header>新增供应商</template>
      <div class="form">
        <el-input v-model="newRow.name" placeholder="供应商名" style="width: 180px" />
        <el-input v-model="newRow.contact" placeholder="联系人" style="width: 140px" />
        <el-input v-model="newRow.phone" placeholder="手机" style="width: 160px" />
        <el-button type="primary" @click="submitNew">确定</el-button>
        <el-button @click="showNew = false">取消</el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.toolbar { display: flex; gap: 10px; align-items: center; margin-bottom: 16px; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 14px; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
.name { font-weight: 600; font-size: 15px; }
.sid { color: var(--muted); font-size: 12px; }
.info { display: flex; flex-direction: column; gap: 6px; font-size: 13px; }
.info .k { display: inline-block; width: 48px; color: var(--muted); }
.form { display: flex; flex-direction: column; gap: 8px; }
.ops { display: flex; gap: 8px; }
.new-card { margin-top: 16px; }
</style>
