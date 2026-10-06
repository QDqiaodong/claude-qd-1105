<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const emit = defineEmits(['changed'])

const suppliers = ref([])
const keyword = ref('')
const statusFilter = ref('') // '' 全部 / 启用 / 停用
const loading = ref(false)
const editingId = ref(null)
const draft = ref({ name: '', contact: '', phone: '' })

const showNew = ref(false)
const newRow = ref({ name: '', contact: '', phone: '' })

// 停用弹窗
const disableVisible = ref(false)
const disableTarget = ref(null)
const disableReason = ref('')
const switching = ref(false)

function fmtTime(t) {
  if (!t) return '—'
  const s = String(t)
  // 后端返回 yyyy-MM-ddTHH:mm:ss，截成 yyyy-MM-dd HH:mm
  return s.length >= 16 ? s.slice(0, 16).replace('T', ' ') : s
}

async function load() {
  loading.value = true
  try {
    suppliers.value = await api.listSuppliers(keyword.value || '', statusFilter.value || '')
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
    // 编辑资料只改名称/联系人/手机，状态与停用原因走专门的停用/启用入口
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
    ElMessage.success('新增成功（默认启用）')
    showNew.value = false
    await load()
    emit('changed')
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function openDisable(s) {
  disableTarget.value = s
  disableReason.value = ''
  disableVisible.value = true
}

async function confirmDisable() {
  if (!disableReason.value || !disableReason.value.trim()) {
    ElMessage.warning('请填写停用原因')
    return
  }
  switching.value = true
  try {
    await api.disableSupplier(disableTarget.value.id, disableReason.value.trim())
    ElMessage.success('已停用')
    disableVisible.value = false
    await load()
    emit('changed')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    switching.value = false
  }
}

async function confirmEnable(s) {
  switching.value = true
  try {
    await api.enableSupplier(s.id)
    ElMessage.success('已重新启用，相关草稿可继续入库')
    await load()
    emit('changed')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    switching.value = false
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
      <el-radio-group v-model="statusFilter" size="default" @change="load">
        <el-radio-button label="">全部</el-radio-button>
        <el-radio-button label="启用">启用</el-radio-button>
        <el-radio-button label="停用">停用</el-radio-button>
      </el-radio-group>
      <el-button @click="openNew">新增供应商</el-button>
    </div>

    <div v-loading="loading" class="grid">
      <el-card v-for="s in suppliers" :key="s.id" class="card" :class="{ off: s.status === '停用' }" shadow="hover">
        <template #header>
          <div class="card-head">
            <span class="name">{{ s.name }}</span>
            <el-tag :type="s.status === '停用' ? 'danger' : 'success'" size="small" effect="dark">
              {{ s.status || '启用' }}
            </el-tag>
          </div>
          <div class="sid">#{{ s.id }}</div>
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
          <div class="status-row">
            <span class="k">停用原因</span>
            <span :class="s.status === '停用' ? 'reason' : 'muted'">
              {{ s.status === '停用' ? (s.inactiveReason || '—') : '—' }}
            </span>
          </div>
          <div><span class="k">变更时间</span><span class="muted">{{ fmtTime(s.statusChangedAt) }}</span></div>
          <div class="ops">
            <el-button link type="primary" size="small" @click="startEdit(s)">编辑资料</el-button>
            <el-button v-if="s.status !== '停用'" link type="danger" size="small" @click="openDisable(s)">停用</el-button>
            <el-button v-else link type="success" size="small" :loading="switching" @click="confirmEnable(s)">
              重新启用
            </el-button>
          </div>
        </div>
      </el-card>
      <el-empty v-if="!loading && !suppliers.length" description="没有符合条件的供应商" :image-size="70" class="empty" />
    </div>

    <el-card v-if="showNew" class="new-card" shadow="never">
      <template #header>新增供应商（默认启用）</template>
      <div class="form-inline">
        <el-input v-model="newRow.name" placeholder="供应商名" style="width: 180px" />
        <el-input v-model="newRow.contact" placeholder="联系人" style="width: 140px" />
        <el-input v-model="newRow.phone" placeholder="手机" style="width: 160px" />
        <el-button type="primary" @click="submitNew">确定</el-button>
        <el-button @click="showNew = false">取消</el-button>
      </div>
    </el-card>

    <!-- 停用原因弹窗：停用必须留原因，便于追溯 -->
    <el-dialog v-model="disableVisible" title="停用供应商" width="460px">
      <div v-if="disableTarget" class="disable-tip">
        停用后，将不能再向供应商「<b>{{ disableTarget.name }}</b>」新建采购草稿；
        已挂在该供应商下、尚未入库的草稿仍可查看与补充明细，但<b>不能入库</b>，重新启用后方可恢复。
      </div>
      <el-input
        v-model="disableReason"
        type="textarea"
        :rows="3"
        maxlength="255"
        show-word-limit
        placeholder="请填写停用原因（必填），例如：合作到期 / 资质过期 / 质量问题"
      />
      <template #footer>
        <el-button @click="disableVisible = false">取消</el-button>
        <el-button type="danger" :loading="switching" @click="confirmDisable">确认停用</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar { display: flex; gap: 10px; align-items: center; margin-bottom: 16px; flex-wrap: wrap; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 14px; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
.name { font-weight: 600; font-size: 15px; }
.sid { color: var(--muted); font-size: 12px; margin-top: 2px; }
.card.off { background: #faf7f7; }
.info { display: flex; flex-direction: column; gap: 6px; font-size: 13px; }
.info .k { display: inline-block; width: 64px; color: var(--muted); flex-shrink: 0; }
.status-row { display: flex; align-items: flex-start; }
.reason { color: #c45656; word-break: break-all; }
.muted { color: var(--muted); }
.form { display: flex; flex-direction: column; gap: 8px; }
.form-inline { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
.ops { display: flex; gap: 8px; margin-top: 2px; }
.new-card { margin-top: 16px; }
.empty { grid-column: 1 / -1; }
.disable-tip { font-size: 13px; color: var(--muted); line-height: 1.7; margin-bottom: 12px; }
</style>
