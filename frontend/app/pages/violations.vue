<template>
  <section class="page violation-page">
    <header class="page__header">
      <div>
        <div class="page__eyebrow"><span class="eyebrow-dot"/>停车秩序</div>
        <h1 class="page__title">违规管理</h1>
        <p class="page__desc">集中复核违规证据、维护计分规则，并跟踪车辆处罚状态</p>
      </div>
      <div v-if="activeTab === 'records'" class="header-actions">
        <button class="button button--ghost" type="button" @click="exportRecords">
          <span class="material-icons-outlined">download</span>导出记录
        </button>
        <button v-if="can('violation:manage')" class="button button--primary" type="button" @click="openCreateRecord">
          <span class="material-icons-outlined">add</span>新增违规
        </button>
      </div>
    </header>

    <section class="workspace">
      <nav aria-label="违规管理分类" class="tabs">
        <button
            v-for="tab in tabs"
            :key="tab.key"
            :class="{ 'tab--active': activeTab === tab.key }"
            class="tab"
            type="button"
            @click="switchTab(tab.key)"
        >
          <span class="material-icons-outlined">{{ tab.icon }}</span>
          {{ tab.label }}
          <span v-if="tab.key === 'records' && summary.pending" class="tab__count">{{ summary.pending }}</span>
        </button>
      </nav>

      <template v-if="activeTab === 'records'">
        <div aria-label="违规概况" class="risk-ribbon">
          <div class="risk-ribbon__intro">
            <span class="material-icons-outlined">radar</span>
            <div><strong>风险雷达</strong><span>今日处理优先级</span></div>
          </div>
          <div class="risk-metric">
            <span>待复核</span><strong>{{ summary.pending }}</strong><small>条记录等待处理</small>
          </div>
          <div class="risk-metric">
            <span>临近阈值</span><strong>{{ summary.near_threshold }}</strong><small>辆车需重点关注</small>
          </div>
          <div class="risk-metric risk-metric--danger">
            <span>处罚中</span><strong>{{ summary.active_penalties }}</strong><small>辆车限制通行</small>
          </div>
          <div class="threshold-scale">
            <div class="threshold-scale__head"><span>处罚分值线</span><strong>{{ summary.threshold }} 分</strong></div>
            <div class="threshold-scale__track"><span style="width: 70%"/><i/></div>
            <div class="threshold-scale__labels"><span>关注线 {{
                Math.max(1, Math.floor(summary.threshold * 0.7))
              }}</span><span>处罚线 {{ summary.threshold }}</span></div>
          </div>
        </div>

        <div class="panel-toolbar">
          <div class="filters">
            <label class="search-input">
              <span class="material-icons-outlined">search</span>
              <input v-model.trim="recordFilters.keyword" placeholder="搜索车牌、车主或地点" type="search"
                     @keyup.enter="searchRecords">
            </label>
            <select v-model="recordFilters.typeId" aria-label="违规类型" class="control">
              <option value="">全部违规类型</option>
              <option v-for="rule in violationTypes" :key="rule.id" :value="String(rule.id)">{{ rule.name }}</option>
            </select>
            <select v-model="recordFilters.status" aria-label="处理状态" class="control">
              <option value="">全部状态</option>
              <option value="PENDING">待复核</option>
              <option value="CONFIRMED">已确认</option>
              <option value="HANDLED">已处理</option>
              <option value="CANCELLED">已撤销</option>
            </select>
            <label class="date-control"><span>从</span><input v-model="recordFilters.startDate" type="date"></label>
            <label class="date-control"><span>至</span><input v-model="recordFilters.endDate" type="date"></label>
          </div>
          <div class="toolbar-actions">
            <button v-if="selectedIds.length" class="button button--danger-soft" type="button" @click="removeSelected">
              <span class="material-icons-outlined">delete</span>删除 {{ selectedIds.length }} 项
            </button>
            <button class="button button--soft" type="button" @click="searchRecords"><span
                class="material-icons-outlined">search</span>筛选
            </button>
            <button aria-label="重置筛选" class="icon-button" title="重置筛选" type="button"
                    @click="resetRecordFilters"><span class="material-icons-outlined">restart_alt</span></button>
          </div>
        </div>

        <div v-if="recordLoading" class="state"><span class="spinner"/>正在加载违规记录</div>
        <div v-else-if="recordError" class="state state--error"><span
            class="material-icons-outlined">error_outline</span>{{ recordError }}
          <button type="button" @click="loadRecords">重新加载</button>
        </div>
        <div v-else class="table-wrap">
          <table class="table records-table">
            <thead>
            <tr>
              <th class="check-cell"><input :checked="allVisibleSelected" aria-label="选择当前页" type="checkbox"
                                            @change="toggleVisibleSelection"></th>
              <th>违规车辆</th>
              <th>违规事项</th>
              <th>记分</th>
              <th>发生地点</th>
              <th>上报时间</th>
              <th>处理状态</th>
              <th class="actions-cell">操作</th>
            </tr>
            </thead>
            <tbody>
            <tr v-for="record in records" :key="record.id"
                :class="{ 'row--selected': selectedIds.includes(record.id) }">
              <td class="check-cell"><input v-model="selectedIds" :aria-label="`选择 ${record.subject_number}`" :value="record.id"
                                            type="checkbox"></td>
              <td>
                <button class="vehicle-identity" type="button" @click="openDetails(record)"><span
                    class="vehicle-icon"><span
                    class="material-icons-outlined">directions_car</span></span><span><strong>{{
                    record.subject_number
                  }}</strong><small>{{ record.subject_name || '未登记车主' }}</small></span></button>
              </td>
              <td>
                <div class="violation-cell"><strong>{{ record.type_name }}</strong><span>{{
                    record.description || '暂无说明'
                  }}</span></div>
              </td>
              <td><span :class="scoreClass(record.score)" class="score-chip">+{{ record.score }}</span></td>
              <td class="text-sub">{{ record.location || '未记录' }}</td>
              <td class="time-cell">{{ formatDateTime(record.violation_time) }}</td>
              <td><span :class="statusMeta(record.status).className"
                        class="status-tag"><i/>{{ statusMeta(record.status).label }}</span>
              </td>
              <td class="actions-cell">
                <button class="text-action" type="button" @click="openDetails(record)">详情</button>
                <button v-if="record.status === 'PENDING'" class="text-action text-action--accent" type="button"
                        @click="openHandling(record)">处理
                </button>
                <button v-if="can('violation:manage')" class="row-icon row-icon--danger" title="删除" type="button"
                        @click="removeRecord(record)"><span class="material-icons-outlined">delete_outline</span>
                </button>
              </td>
            </tr>
            <tr v-if="records.length === 0">
              <td colspan="8">
                <div class="empty-state"><span
                    class="material-icons-outlined">task_alt</span><strong>没有匹配的违规记录</strong><span>调整筛选条件，或新增一条违规记录</span>
                </div>
              </td>
            </tr>
            </tbody>
          </table>
        </div>
        <footer v-if="!recordLoading && !recordError" class="pagination">
          <span class="pagination__info">共 {{ recordTotal }} 条，当前第 {{ recordPage }} / {{
              recordTotalPages
            }} 页</span>
          <button :disabled="recordPage <= 1" aria-label="上一页" type="button"
                  @click="changeRecordPage(recordPage - 1)"><span class="material-icons-outlined">chevron_left</span>
          </button>
          <button v-for="pageNumber in recordPageNumbers" :key="pageNumber" :class="{ active: pageNumber === recordPage }"
                  type="button" @click="changeRecordPage(pageNumber)">{{ pageNumber }}
          </button>
          <button :disabled="recordPage >= recordTotalPages" aria-label="下一页" type="button"
                  @click="changeRecordPage(recordPage + 1)"><span class="material-icons-outlined">chevron_right</span>
          </button>
        </footer>
      </template>

      <template v-else-if="activeTab === 'rules'">
        <section class="rule-overview">
          <div class="rule-overview__copy">
            <span class="rule-overview__icon"><span class="material-icons-outlined">speed</span></span>
            <div><span class="section-kicker">累计处罚标准</span>
              <h2>达到 <strong>{{ setting.score_threshold }}</strong> 分后限制通行</h2>
              <p>处罚默认持续 {{ setting.punishment_days }} 天。历史记录保留原始分值，不受规则后续调整影响。</p></div>
          </div>
          <div aria-hidden="true" class="score-ruler">
            <span v-for="tick in 7" :key="tick" :class="{ 'score-ruler__tick--major': tick === 7 }"><i/>{{
                Math.round((setting.score_threshold / 6) * (tick - 1))
              }}</span>
          </div>
          <button class="button button--ghost" type="button" @click="settingModalVisible = true"><span
              class="material-icons-outlined">tune</span>调整处罚标准
          </button>
        </section>

        <div class="section-head">
          <div><h2>计分规则</h2>
            <p>分值会在记录创建时固化，保证历史统计口径不变</p></div>
          <button v-if="can('violation:manage')" class="button button--primary" type="button" @click="openCreateRule">
            <span class="material-icons-outlined">add</span>新增规则
          </button>
        </div>
        <div v-if="typesLoading" class="state"><span class="spinner"/>正在加载计分规则</div>
        <div v-else class="table-wrap">
          <table class="table rules-table">
            <thead>
            <tr>
              <th>排序</th>
              <th>违规类型</th>
              <th>记分</th>
              <th>规则说明</th>
              <th>状态</th>
              <th class="actions-cell">操作</th>
            </tr>
            </thead>
            <tbody>
            <tr v-for="rule in violationTypes" :key="rule.id">
              <td class="order-number">{{ String(rule.sort_order).padStart(2, '0') }}</td>
              <td><strong>{{ rule.name }}</strong></td>
              <td><span :class="scoreClass(rule.score)" class="score-chip">+{{ rule.score }}</span></td>
              <td class="rule-description">{{ rule.description || '暂无说明' }}</td>
              <td><span :class="rule.status === 1 ? 'status-tag--success' : 'status-tag--neutral'"
                        class="status-tag"><i/>{{
                  rule.status === 1 ? '启用' : '停用'
                }}</span></td>
              <td class="actions-cell">
                <button v-if="can('violation:manage')" class="text-action" type="button" @click="openEditRule(rule)">
                  编辑
                </button>
                <button v-if="can('violation:manage')" class="row-icon row-icon--danger" title="删除" type="button"
                        @click="removeRule(rule)"><span class="material-icons-outlined">delete_outline</span></button>
              </td>
            </tr>
            <tr v-if="violationTypes.length === 0">
              <td colspan="6">
                <div class="empty-state"><span
                    class="material-icons-outlined">rule</span><strong>还没有计分规则</strong><span>新增规则后即可录入违规记录</span>
                </div>
              </td>
            </tr>
            </tbody>
          </table>
        </div>
      </template>

      <template v-else>
        <div class="penalty-notice">
          <span class="penalty-notice__mark"><span class="material-icons-outlined">no_crash</span></span>
          <div><strong>处罚名单按已确认记录实时计算</strong><span>累计分达到 {{ setting.score_threshold }} 分后进入限制；解除后仍保留完整违规历史。</span>
          </div>
          <span class="penalty-notice__count">{{ activePenaltyCount }}<small>处罚中</small></span>
        </div>
        <div class="panel-toolbar">
          <div class="filters">
            <label class="search-input"><span class="material-icons-outlined">search</span><input
                v-model.trim="penaltyFilters.keyword" placeholder="搜索车牌或车主" type="search"
                @keyup.enter="searchPenalties"></label>
            <label class="score-filter"><span>最低分</span><input v-model.number="penaltyFilters.minScore" :placeholder="String(setting.score_threshold)"
                                                                  min="0"
                                                                  type="number"></label>
            <label class="date-control"><span>处罚从</span><input v-model="penaltyFilters.startDate"
                                                                  type="date"></label>
            <label class="date-control"><span>至</span><input v-model="penaltyFilters.endDate" type="date"></label>
          </div>
          <div class="toolbar-actions">
            <button class="button button--soft" type="button" @click="searchPenalties"><span
                class="material-icons-outlined">search</span>筛选
            </button>
            <button class="icon-button" title="重置筛选" type="button" @click="resetPenaltyFilters"><span
                class="material-icons-outlined">restart_alt</span></button>
          </div>
        </div>
        <div v-if="penaltyLoading" class="state"><span class="spinner"/>正在加载处罚名单</div>
        <div v-else-if="penaltyError" class="state state--error"><span
            class="material-icons-outlined">error_outline</span>{{ penaltyError }}
          <button type="button" @click="loadPenalties">重新加载</button>
        </div>
        <div v-else class="table-wrap">
          <table class="table penalty-table">
            <thead>
            <tr>
              <th>违规车辆</th>
              <th>累计分值</th>
              <th>处罚开始</th>
              <th>预计到期</th>
              <th>状态</th>
              <th class="actions-cell">操作</th>
            </tr>
            </thead>
            <tbody>
            <tr v-for="penalty in penalties" :key="penalty.subject_id">
              <td>
                <div class="vehicle-identity vehicle-identity--static"><span
                    class="vehicle-icon vehicle-icon--danger"><span
                    class="material-icons-outlined">directions_car</span></span><span><strong>{{
                    penalty.subject_number
                  }}</strong><small>{{ penalty.subject_name }}</small></span></div>
              </td>
              <td>
                <div class="score-progress">
                  <div><strong>{{ penalty.total_score }}</strong><span>/ {{ setting.score_threshold }} 分</span></div>
                  <span class="score-progress__track"><i
                      :style="{ width: `${Math.min(100, penalty.total_score / setting.score_threshold * 100)}%` }"/></span>
                </div>
              </td>
              <td class="time-cell">{{ formatDateTime(penalty.punishment_at) }}</td>
              <td class="time-cell">{{ formatDateTime(penalty.expires_at) }}</td>
              <td><span :class="penaltyStatusMeta(penalty.status).className" class="status-tag"><i/>{{
                  penaltyStatusMeta(penalty.status).label
                }}</span></td>
              <td class="actions-cell">
                <button v-if="penalty.status !== 'RELEASED'" class="button button--release" type="button"
                        @click="openRelease(penalty)"><span class="material-icons-outlined">lock_open</span>解除处罚
                </button>
                <span v-else class="released-text">已解除</span></td>
            </tr>
            <tr v-if="penalties.length === 0">
              <td colspan="6">
                <div class="empty-state"><span
                    class="material-icons-outlined">verified</span><strong>当前没有处罚车辆</strong><span>达到处罚分值线的车辆会出现在这里</span>
                </div>
              </td>
            </tr>
            </tbody>
          </table>
        </div>
        <footer v-if="!penaltyLoading && !penaltyError" class="pagination"><span
            class="pagination__info">共 {{ penaltyTotal }} 条</span>
          <button :disabled="penaltyPage <= 1" type="button" @click="changePenaltyPage(penaltyPage - 1)"><span
              class="material-icons-outlined">chevron_left</span></button>
          <button v-for="pageNumber in penaltyPageNumbers" :key="pageNumber" :class="{ active: pageNumber === penaltyPage }"
                  type="button" @click="changePenaltyPage(pageNumber)">{{
              pageNumber
            }}
          </button>
          <button :disabled="penaltyPage >= penaltyTotalPages" type="button"
                  @click="changePenaltyPage(penaltyPage + 1)"><span class="material-icons-outlined">chevron_right</span>
          </button>
        </footer>
      </template>
    </section>

    <div v-if="detailRecord" class="drawer-mask" @click.self="detailRecord = null">
      <aside aria-label="违规详情" class="drawer">
        <header class="drawer__head">
          <div><span class="section-kicker">违规记录 #{{ String(detailRecord.id).padStart(4, '0') }}</span>
            <h2>{{ detailRecord.subject_number }}</h2></div>
          <button aria-label="关闭详情" class="icon-button" type="button" @click="detailRecord = null"><span
              class="material-icons-outlined">close</span></button>
        </header>
        <div class="drawer__body">
          <div v-if="isImageEvidence(detailRecord.evidence)" class="evidence"><img :alt="`${detailRecord.subject_number} 违规证据`"
                                                                                   :src="detailRecord.evidence || ''"><span>违规证据</span>
          </div>
          <div v-else class="evidence evidence--empty"><span class="material-icons-outlined">photo_camera</span><strong>暂无可预览图片</strong><small>{{
              detailRecord.evidence || '该记录未上传证据附件'
            }}</small></div>
          <div class="detail-hero">
            <div><span>违规事项</span><strong>{{ detailRecord.type_name }}</strong></div>
            <span :class="scoreClass(detailRecord.score)" class="score-chip score-chip--large">+{{ detailRecord.score }} 分</span>
          </div>
          <dl class="detail-list">
            <div>
              <dt>处理状态</dt>
              <dd><span :class="statusMeta(detailRecord.status).className" class="status-tag"><i/>{{
                  statusMeta(detailRecord.status).label
                }}</span></dd>
            </div>
            <div>
              <dt>车主</dt>
              <dd>{{ detailRecord.subject_name || '未登记' }}</dd>
            </div>
            <div>
              <dt>发生地点</dt>
              <dd>{{ detailRecord.location || '未记录' }}</dd>
            </div>
            <div>
              <dt>违规时间</dt>
              <dd>{{ formatDateTime(detailRecord.violation_time) }}</dd>
            </div>
            <div>
              <dt>处理人</dt>
              <dd>{{ detailRecord.handler_name || '尚未分配' }}</dd>
            </div>
            <div>
              <dt>处理时间</dt>
              <dd>{{ formatDateTime(detailRecord.handled_at) }}</dd>
            </div>
          </dl>
          <section class="detail-note"><span>违规说明</span>
            <p>{{ detailRecord.description || '暂无违规说明' }}</p></section>
          <section v-if="detailRecord.handling_remark" class="detail-note"><span>处理备注</span>
            <p>{{ detailRecord.handling_remark }}</p></section>
        </div>
        <footer class="drawer__foot">
          <button class="button button--ghost" type="button" @click="detailRecord = null">关闭</button>
          <button v-if="detailRecord.status === 'PENDING'" class="button button--primary" type="button"
                  @click="openHandling(detailRecord)"><span class="material-icons-outlined">fact_check</span>处理记录
          </button>
        </footer>
      </aside>
    </div>

    <div v-if="recordModalVisible" class="modal-mask" @click.self="recordModalVisible = false">
      <form class="modal" @submit.prevent="saveRecord">
        <header class="modal__head">
          <div><span class="section-kicker">人工录入</span>
            <h2>新增违规记录</h2></div>
          <button class="icon-button" type="button" @click="recordModalVisible = false"><span
              class="material-icons-outlined">close</span></button>
        </header>
        <div class="modal__body">
          <div class="form-grid"><label class="field"><span>车牌号</span><input v-model.trim="recordForm.subjectNumber"
                                                                                placeholder="例如：粤A·12345"
                                                                                required></label><label
              class="field"><span>车主名称</span><input v-model.trim="recordForm.subjectName" placeholder="请输入车主名称"
                                                        required></label><label class="field"><span>违规类型</span><select
              v-model.number="recordForm.typeId" required>
            <option :value="null">请选择违规类型</option>
            <option v-for="rule in activeViolationTypes" :key="rule.id" :value="rule.id">{{ rule.name }}（{{
                rule.score
              }} 分）
            </option>
          </select></label><label class="field"><span>违规时间</span><input v-model="recordForm.violationTime"
                                                                            required
                                                                            type="datetime-local"></label><label
              class="field field--wide"><span>发生地点</span><input v-model.trim="recordForm.location"
                                                                    placeholder="例如：一号车场东侧通道"></label><label
              class="field field--wide"><span>证据图片地址</span><input v-model.trim="recordForm.evidenceInfo"
                                                                        placeholder="选填，支持 http(s) 图片地址"
                                                                        type="url"></label>
          </div>
          <p v-if="formError" class="form-error">{{ formError }}</p></div>
        <footer class="modal__foot">
          <button class="button button--ghost" type="button" @click="recordModalVisible = false">取消</button>
          <button :disabled="saving" class="button button--primary" type="submit"><span v-if="saving"
                                                                                        class="spinner spinner--button"/>保存记录
          </button>
        </footer>
      </form>
    </div>

    <div v-if="handlingRecord" class="modal-mask" @click.self="handlingRecord = null">
      <form class="modal modal--compact" @submit.prevent="saveHandling">
        <header class="modal__head">
          <div><span class="section-kicker">{{ handlingRecord.subject_number }}</span>
            <h2>处理违规记录</h2></div>
          <button class="icon-button" type="button" @click="handlingRecord = null"><span
              class="material-icons-outlined">close</span></button>
        </header>
        <div class="modal__body">
          <div class="decision-cards"><label :class="{ selected: handlingForm.status === 'HANDLED' }"><input
              v-model="handlingForm.status" type="radio" value="HANDLED"><span class="material-icons-outlined">check_circle</span><strong>确认违规</strong><small>计入累计分值</small></label><label
              :class="{ selected: handlingForm.status === 'CANCELLED' }"><input v-model="handlingForm.status"
                                                                                type="radio" value="CANCELLED"><span
              class="material-icons-outlined">cancel</span><strong>撤销记录</strong><small>不计入累计分值</small></label>
          </div>
          <label class="field"><span>处理备注</span><textarea v-model.trim="handlingForm.remark" placeholder="填写核验结论或补充说明"
                                                              rows="4"/></label>
          <p v-if="formError" class="form-error">{{ formError }}</p></div>
        <footer class="modal__foot">
          <button class="button button--ghost" type="button" @click="handlingRecord = null">取消</button>
          <button :disabled="saving" class="button button--primary" type="submit">保存处理结果</button>
        </footer>
      </form>
    </div>

    <div v-if="ruleModalVisible" class="modal-mask" @click.self="ruleModalVisible = false">
      <form class="modal modal--compact" @submit.prevent="saveRule">
        <header class="modal__head">
          <div><span class="section-kicker">计分规则</span>
            <h2>{{ editingRuleId ? '编辑规则' : '新增规则' }}</h2></div>
          <button class="icon-button" type="button" @click="ruleModalVisible = false"><span
              class="material-icons-outlined">close</span></button>
        </header>
        <div class="modal__body">
          <div class="form-grid"><label class="field field--wide"><span>违规类型</span><input
              v-model.trim="ruleForm.name" maxlength="32" placeholder="例如：堵塞消防通道" required></label><label
              class="field"><span>违规分值</span><input v-model.number="ruleForm.score" max="100" min="1" required
                                                        type="number"></label><label
              class="field"><span>显示顺序</span><input v-model.number="ruleForm.sortOrder" min="0" required
                                                        type="number"></label><label class="field"><span>状态</span><select
              v-model.number="ruleForm.status">
            <option :value="1">启用</option>
            <option :value="0">停用</option>
          </select></label><label class="field field--wide"><span>规则说明</span><textarea
              v-model.trim="ruleForm.description" maxlength="255" placeholder="说明违规判定标准" rows="3"/></label>
          </div>
          <p v-if="formError" class="form-error">{{ formError }}</p></div>
        <footer class="modal__foot">
          <button class="button button--ghost" type="button" @click="ruleModalVisible = false">取消</button>
          <button :disabled="saving" class="button button--primary" type="submit">保存规则</button>
        </footer>
      </form>
    </div>

    <div v-if="settingModalVisible" class="modal-mask" @click.self="settingModalVisible = false">
      <form class="modal modal--compact" @submit.prevent="saveSetting">
        <header class="modal__head">
          <div><span class="section-kicker">全局设置</span>
            <h2>调整处罚标准</h2></div>
          <button class="icon-button" type="button" @click="settingModalVisible = false"><span
              class="material-icons-outlined">close</span></button>
        </header>
        <div class="modal__body"><p class="modal-hint">
          修改后会按新的阈值重新判断现有车辆状态，历史违规记录的单次分值不会改变。</p>
          <div class="form-grid"><label class="field"><span>累计分值上限</span>
            <div class="number-suffix"><input v-model.number="settingForm.scoreThreshold" max="100" min="1"
                                              required type="number"><em>分</em></div>
          </label><label class="field"><span>处罚持续时间</span>
            <div class="number-suffix"><input v-model.number="settingForm.punishmentDays" max="365" min="1"
                                              required type="number"><em>天</em></div>
          </label></div>
          <p v-if="formError" class="form-error">{{ formError }}</p></div>
        <footer class="modal__foot">
          <button class="button button--ghost" type="button" @click="settingModalVisible = false">取消</button>
          <button :disabled="saving" class="button button--primary" type="submit">保存标准</button>
        </footer>
      </form>
    </div>

    <div v-if="releasingPenalty" class="modal-mask" @click.self="releasingPenalty = null">
      <form class="modal modal--compact" @submit.prevent="releasePenalty">
        <header class="modal__head">
          <div><span class="section-kicker">解除处罚</span>
            <h2>{{ releasingPenalty.subject_number }}</h2></div>
          <button class="icon-button" type="button" @click="releasingPenalty = null"><span
              class="material-icons-outlined">close</span></button>
        </header>
        <div class="modal__body">
          <div class="release-warning"><span class="material-icons-outlined">lock_open</span>
            <div><strong>确认恢复该车辆的通行状态？</strong>
              <p>该车辆当前累计 {{ releasingPenalty.total_score }} 分。解除处罚不会删除历史违规记录。</p></div>
          </div>
          <label class="field"><span>解除说明</span><textarea v-model.trim="releaseRemark" placeholder="选填，记录解除原因"
                                                              rows="3"/></label>
          <p v-if="formError" class="form-error">{{ formError }}</p></div>
        <footer class="modal__foot">
          <button class="button button--ghost" type="button" @click="releasingPenalty = null">取消</button>
          <button :disabled="saving" class="button button--primary" type="submit">确认解除</button>
        </footer>
      </form>
    </div>
  </section>
</template>

<script lang="ts" setup>
type TabKey = "records" | "rules" | "penalties";

interface ViolationRecord {
  id: number;
  subject_id: number;
  subject_name: string;
  subject_number: string;
  type_id: number;
  type_name: string;
  score: number;
  description?: string | null;
  location?: string | null;
  evidence?: string | null;
  status: string;
  handler_name?: string | null;
  handling_remark?: string | null;
  violation_time: string;
  handled_at?: string | null;
  created_at: string;
}

interface ViolationSummary {
  pending: number;
  near_threshold: number;
  active_penalties: number;
  threshold: number
}

interface ViolationList {
  items: ViolationRecord[];
  total: number;
  summary: ViolationSummary
}

interface ViolationType {
  id: number;
  name: string;
  score: number;
  description?: string | null;
  status: number;
  sort_order: number;
  created_at: string
}

interface ViolationTypeList {
  items: ViolationType[]
}

interface ViolationSetting {
  score_threshold: number;
  punishment_days: number;
  updated_at: string
}

interface Penalty {
  subject_id: number;
  subject_name: string;
  subject_number: string;
  total_score: number;
  punishment_at: string;
  expires_at: string;
  status: string
}

interface PenaltyList {
  items: Penalty[];
  total: number
}

const http = useHttp();
const {can} = usePermission();
const authStore = useAuthStore();
const tabs: { key: TabKey; label: string; icon: string }[] = [
  {key: "records", label: "违规记录", icon: "fact_check"},
  {key: "rules", label: "计分规则", icon: "rule"},
  {key: "penalties", label: "处罚名单", icon: "no_crash"},
];
const activeTab = ref<TabKey>("records");
const records = ref<ViolationRecord[]>([]);
const recordTotal = ref(0);
const recordPage = ref(1);
const pageSize = 10;
const recordLoading = ref(true);
const recordError = ref("");
const selectedIds = ref<number[]>([]);
const summary = reactive<ViolationSummary>({pending: 0, near_threshold: 0, active_penalties: 0, threshold: 12});
const recordFilters = reactive({keyword: "", typeId: "", status: "", startDate: "", endDate: ""});
const violationTypes = ref<ViolationType[]>([]);
const typesLoading = ref(true);
const setting = reactive<ViolationSetting>({score_threshold: 12, punishment_days: 30, updated_at: ""});
const penalties = ref<Penalty[]>([]);
const penaltyTotal = ref(0);
const penaltyPage = ref(1);
const penaltyLoading = ref(false);
const penaltyError = ref("");
const penaltyFilters = reactive({keyword: "", minScore: null as number | null, startDate: "", endDate: ""});
const detailRecord = ref<ViolationRecord | null>(null);
const handlingRecord = ref<ViolationRecord | null>(null);
const releasingPenalty = ref<Penalty | null>(null);
const recordModalVisible = ref(false);
const ruleModalVisible = ref(false);
const settingModalVisible = ref(false);
const editingRuleId = ref<number | null>(null);
const saving = ref(false);
const formError = ref("");
const releaseRemark = ref("");
const recordForm = reactive({
  subjectNumber: "",
  subjectName: "",
  typeId: null as number | null,
  violationTime: "",
  location: "",
  evidenceInfo: ""
});
const handlingForm = reactive({status: "HANDLED", remark: ""});
const ruleForm = reactive({name: "", score: 2, description: "", status: 1, sortOrder: 0});
const settingForm = reactive({scoreThreshold: 12, punishmentDays: 30});

const recordTotalPages = computed(() => Math.max(1, Math.ceil(recordTotal.value / pageSize)));
const recordPageNumbers = computed(() => paginationNumbers(recordPage.value, recordTotalPages.value));
const penaltyTotalPages = computed(() => Math.max(1, Math.ceil(penaltyTotal.value / pageSize)));
const penaltyPageNumbers = computed(() => paginationNumbers(penaltyPage.value, penaltyTotalPages.value));
const allVisibleSelected = computed(() => records.value.length > 0 && records.value.every((record) => selectedIds.value.includes(record.id)));
const activeViolationTypes = computed(() => violationTypes.value.filter((rule) => rule.status === 1));
const activePenaltyCount = computed(() => summary.active_penalties);

function paginationNumbers(current: number, total: number) {
  const start = Math.max(1, Math.min(current - 2, total - 4));
  return Array.from({length: Math.min(5, total)}, (_, index) => Math.max(1, start) + index);
}

function apiError(error: unknown, fallback: string) {
  return (error as { statusMessage?: string }).statusMessage || fallback;
}

function toStartTime(date: string) {
  return date ? `${date}T00:00:00` : undefined;
}

function toEndTime(date: string) {
  return date ? `${date}T23:59:59` : undefined;
}

function nowForInput() {
  const date = new Date();
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 16);
}

function formatDateTime(value?: string | null) {
  return value ? value.replace("T", " ").slice(0, 16) : "—";
}

function isImageEvidence(value?: string | null) {
  return Boolean(value && /^(https?:\/\/|data:image\/|\/uploads\/)/i.test(value));
}

function scoreClass(score: number) {
  return score >= 6 ? "score-chip--high" : score >= 3 ? "score-chip--medium" : "score-chip--low";
}

function statusMeta(status: string) {
  return ({
    PENDING: {label: "待复核", className: "status-tag--warning"},
    CONFIRMED: {label: "已确认", className: "status-tag--info"},
    HANDLED: {label: "已处理", className: "status-tag--success"},
    CANCELLED: {label: "已撤销", className: "status-tag--neutral"},
  } as Record<string, { label: string; className: string }>)[status] || {
    label: status,
    className: "status-tag--neutral"
  };
}

function penaltyStatusMeta(status: string) {
  return ({
    ACTIVE: {label: "处罚中", className: "status-tag--danger"},
    EXPIRED: {label: "已到期", className: "status-tag--warning"},
    RELEASED: {label: "已解除", className: "status-tag--success"},
  } as Record<string, { label: string; className: string }>)[status] || {
    label: status,
    className: "status-tag--neutral"
  };
}

async function loadRecords() {
  recordLoading.value = true;
  recordError.value = "";
  try {
    const result = await http.get<ViolationList>("/violations", {
      keyword: recordFilters.keyword || undefined,
      type_id: recordFilters.typeId || undefined,
      status: recordFilters.status || undefined,
      start_time: toStartTime(recordFilters.startDate),
      end_time: toEndTime(recordFilters.endDate),
      page: recordPage.value,
      page_size: pageSize,
    });
    records.value = result.items || [];
    recordTotal.value = result.total || 0;
    Object.assign(summary, result.summary || {
      pending: 0,
      near_threshold: 0,
      active_penalties: 0,
      threshold: setting.score_threshold
    });
    selectedIds.value = selectedIds.value.filter((id) => records.value.some((record) => record.id === id));
  } catch (error) {
    recordError.value = apiError(error, "违规记录加载失败");
  } finally {
    recordLoading.value = false;
  }
}

async function loadTypes() {
  typesLoading.value = true;
  try {
    const result = await http.get<ViolationTypeList>("/violation-types");
    violationTypes.value = result.items || [];
  } catch (error) {
    if (activeTab.value === "rules") recordError.value = apiError(error, "计分规则加载失败");
  } finally {
    typesLoading.value = false;
  }
}

async function loadSetting() {
  try {
    const result = await http.get<ViolationSetting>("/violation-settings");
    Object.assign(setting, result);
    Object.assign(settingForm, {scoreThreshold: result.score_threshold, punishmentDays: result.punishment_days});
  } catch {
    // The visible defaults keep the page usable while the setting endpoint is temporarily unavailable.
  }
}

async function loadPenalties() {
  penaltyLoading.value = true;
  penaltyError.value = "";
  try {
    const result = await http.get<PenaltyList>("/violation-penalties", {
      keyword: penaltyFilters.keyword || undefined,
      min_score: penaltyFilters.minScore ?? undefined,
      start_time: toStartTime(penaltyFilters.startDate),
      end_time: toEndTime(penaltyFilters.endDate),
      page: penaltyPage.value,
      page_size: pageSize,
    });
    penalties.value = result.items || [];
    penaltyTotal.value = result.total || 0;
  } catch (error) {
    penaltyError.value = apiError(error, "处罚名单加载失败");
  } finally {
    penaltyLoading.value = false;
  }
}

function switchTab(tab: TabKey) {
  activeTab.value = tab;
  if (tab === "penalties") void loadPenalties();
}

function searchRecords() {
  recordPage.value = 1;
  void loadRecords();
}

function resetRecordFilters() {
  Object.assign(recordFilters, {keyword: "", typeId: "", status: "", startDate: "", endDate: ""});
  recordPage.value = 1;
  void loadRecords();
}

function changeRecordPage(page: number) {
  if (page < 1 || page > recordTotalPages.value) return;
  recordPage.value = page;
  void loadRecords();
}

function searchPenalties() {
  penaltyPage.value = 1;
  void loadPenalties();
}

function resetPenaltyFilters() {
  Object.assign(penaltyFilters, {keyword: "", minScore: null, startDate: "", endDate: ""});
  penaltyPage.value = 1;
  void loadPenalties();
}

function changePenaltyPage(page: number) {
  if (page < 1 || page > penaltyTotalPages.value) return;
  penaltyPage.value = page;
  void loadPenalties();
}

function toggleVisibleSelection() {
  selectedIds.value = allVisibleSelected.value ? [] : records.value.map((record) => record.id);
}

function openDetails(record: ViolationRecord) {
  detailRecord.value = record;
}

function openCreateRecord() {
  Object.assign(recordForm, {
    subjectNumber: "",
    subjectName: "",
    typeId: activeViolationTypes.value[0]?.id ?? null,
    violationTime: nowForInput(),
    location: "",
    evidenceInfo: ""
  });
  formError.value = "";
  recordModalVisible.value = true;
}

function openHandling(record: ViolationRecord) {
  detailRecord.value = null;
  handlingRecord.value = record;
  Object.assign(handlingForm, {status: "HANDLED", remark: ""});
  formError.value = "";
}

function openCreateRule() {
  editingRuleId.value = null;
  Object.assign(ruleForm, {name: "", score: 2, description: "", status: 1, sortOrder: violationTypes.value.length + 1});
  formError.value = "";
  ruleModalVisible.value = true;
}

function openEditRule(rule: ViolationType) {
  editingRuleId.value = rule.id;
  Object.assign(ruleForm, {
    name: rule.name,
    score: rule.score,
    description: rule.description || "",
    status: rule.status,
    sortOrder: rule.sort_order
  });
  formError.value = "";
  ruleModalVisible.value = true;
}

function openRelease(penalty: Penalty) {
  releasingPenalty.value = penalty;
  releaseRemark.value = "";
  formError.value = "";
}

async function saveRecord() {
  if (!recordForm.typeId) {
    formError.value = "请选择违规类型";
    return;
  }
  saving.value = true;
  formError.value = "";
  try {
    await http.post("/violations", {
      subject_number: recordForm.subjectNumber,
      subject_name: recordForm.subjectName,
      type_id: recordForm.typeId,
      violation_time: `${recordForm.violationTime}:00`,
      location: recordForm.location || undefined,
      evidence_info: recordForm.evidenceInfo || undefined
    });
    recordModalVisible.value = false;
    recordPage.value = 1;
    await loadRecords();
  } catch (error) {
    formError.value = apiError(error, "违规记录保存失败");
  } finally {
    saving.value = false;
  }
}

async function saveHandling() {
  if (!handlingRecord.value) return;
  saving.value = true;
  formError.value = "";
  try {
    await http.put(`/violations/${handlingRecord.value.id}/handling`, {
      status: handlingForm.status,
      handler_name: authStore.user?.username || "管理员",
      handling_remark: handlingForm.remark || undefined
    });
    handlingRecord.value = null;
    await Promise.all([loadRecords(), loadPenalties()]);
  } catch (error) {
    formError.value = apiError(error, "处理结果保存失败");
  } finally {
    saving.value = false;
  }
}

async function saveRule() {
  saving.value = true;
  formError.value = "";
  try {
    const payload = {
      name: ruleForm.name,
      score: ruleForm.score,
      description: ruleForm.description || undefined,
      status: ruleForm.status,
      sort_order: ruleForm.sortOrder
    };
    if (editingRuleId.value) await http.put(`/violation-types/${editingRuleId.value}`, payload);
    else await http.post("/violation-types", payload);
    ruleModalVisible.value = false;
    await loadTypes();
  } catch (error) {
    formError.value = apiError(error, "计分规则保存失败");
  } finally {
    saving.value = false;
  }
}

async function saveSetting() {
  saving.value = true;
  formError.value = "";
  try {
    await http.put("/violation-settings", {
      score_threshold: settingForm.scoreThreshold,
      punishment_days: settingForm.punishmentDays
    });
    settingModalVisible.value = false;
    await Promise.all([loadSetting(), loadRecords(), loadPenalties()]);
  } catch (error) {
    formError.value = apiError(error, "处罚标准保存失败");
  } finally {
    saving.value = false;
  }
}

async function releasePenalty() {
  if (!releasingPenalty.value) return;
  saving.value = true;
  formError.value = "";
  try {
    await http.put(`/violation-penalties/${releasingPenalty.value.subject_id}/release`, {release_remark: releaseRemark.value || undefined});
    releasingPenalty.value = null;
    await Promise.all([loadRecords(), loadPenalties()]);
  } catch (error) {
    formError.value = apiError(error, "解除处罚失败");
  } finally {
    saving.value = false;
  }
}

async function removeRecord(record: ViolationRecord) {
  if (!window.confirm(`确认删除 ${record.subject_number} 的这条违规记录？`)) return;
  try {
    await http.delete(`/violations/${record.id}`);
    await loadRecords();
  } catch (error) {
    recordError.value = apiError(error, "删除失败");
  }
}

async function removeSelected() {
  if (!window.confirm(`确认删除已选择的 ${selectedIds.value.length} 条违规记录？`)) return;
  try {
    for (const id of selectedIds.value) await http.delete(`/violations/${id}`);
    selectedIds.value = [];
    await loadRecords();
  } catch (error) {
    recordError.value = apiError(error, "部分记录删除失败，请刷新后重试");
  }
}

async function removeRule(rule: ViolationType) {
  if (!window.confirm(`确认删除计分规则“${rule.name}”？`)) return;
  try {
    await http.delete(`/violation-types/${rule.id}`);
    await loadTypes();
  } catch (error) {
    window.alert(apiError(error, "删除失败"));
  }
}

function exportRecords() {
  const rows = [["车牌号", "车主", "违规类型", "分值", "发生地点", "违规时间", "处理状态", "处理人"], ...records.value.map((record) => [record.subject_number, record.subject_name, record.type_name, record.score, record.location || "", record.violation_time, statusMeta(record.status).label, record.handler_name || ""])];
  const csv = rows.map((row) => row.map((cell) => `"${String(cell).replaceAll('"', '""')}"`).join(",")).join("\r\n");
  const link = document.createElement("a");
  link.href = URL.createObjectURL(new Blob(["\ufeff" + csv], {type: "text/csv;charset=utf-8"}));
  link.download = `违规记录_${new Date().toISOString().slice(0, 10)}.csv`;
  link.click();
  URL.revokeObjectURL(link.href);
}

onMounted(() => {
  void Promise.all([loadRecords(), loadTypes(), loadSetting()]);
});
</script>

<style scoped>
.violation-page {
  --amber: #b86810;
  --amber-soft: #fff6e8;
  --danger: #c43f3f;
  --danger-soft: #fff0f0;
  --success: #23845a;
  --success-soft: #eaf8f1;
  --ink-blue: #224fa6;
  min-height: 100%;
  padding: 24px
}

.page__header {
  align-items: flex-end;
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  justify-content: space-between;
  margin-bottom: 20px
}

.page__eyebrow {
  align-items: center;
  color: var(--text-mute);
  display: flex;
  font-size: 11px;
  font-weight: 650;
  gap: 7px;
  letter-spacing: .12em;
  margin-bottom: 5px
}

.eyebrow-dot {
  background: var(--primary);
  border-radius: 50%;
  box-shadow: 0 0 0 4px var(--primary-soft);
  height: 6px;
  width: 6px
}

.page__title {
  color: var(--text);
  font-size: 22px;
  font-weight: 680;
  letter-spacing: -.02em;
  margin: 0
}

.page__desc {
  color: var(--text-sub);
  font-size: 13px;
  margin: 4px 0 0
}

.header-actions {
  display: flex;
  gap: 8px
}

.workspace {
  background: var(--card);
  border: 1px solid var(--border-strong);
  border-radius: 10px;
  box-shadow: 0 1px 2px rgb(15 23 42 / 3%);
  overflow: hidden
}

.tabs {
  align-items: center;
  border-bottom: 1px solid var(--border-strong);
  display: flex;
  gap: 4px;
  min-height: 54px;
  padding: 0 18px
}

.tab {
  align-items: center;
  align-self: stretch;
  background: transparent;
  border: 0;
  border-bottom: 2px solid transparent;
  color: var(--text-sub);
  cursor: pointer;
  display: flex;
  font: inherit;
  font-weight: 500;
  gap: 7px;
  margin-bottom: -1px;
  padding: 0 16px;
  position: relative
}

.tab:hover {
  color: var(--text)
}

.tab--active {
  border-bottom-color: var(--primary);
  color: var(--primary)
}

.tab .material-icons-outlined {
  font-size: 18px
}

.tab__count {
  align-items: center;
  background: var(--danger-soft);
  border-radius: 10px;
  color: var(--danger);
  display: inline-flex;
  font-size: 10px;
  font-weight: 700;
  height: 18px;
  justify-content: center;
  min-width: 18px;
  padding: 0 5px
}

.risk-ribbon {
  align-items: stretch;
  background: linear-gradient(90deg, var(--primary-soft), transparent 72%);
  border-bottom: 1px solid var(--border);
  display: grid;
  grid-template-columns:160px repeat(3, minmax(120px, 1fr)) minmax(220px, 1.5fr);
  min-height: 104px;
  padding: 0 20px
}

.risk-ribbon__intro {
  align-items: center;
  display: flex;
  gap: 10px
}

.risk-ribbon__intro > .material-icons-outlined {
  align-items: center;
  background: var(--primary);
  border-radius: 8px;
  color: #fff;
  display: flex;
  font-size: 20px;
  height: 38px;
  justify-content: center;
  width: 38px
}

.risk-ribbon__intro div {
  display: grid
}

.risk-ribbon__intro strong {
  color: var(--text);
  font-size: 13px
}

.risk-ribbon__intro span {
  color: var(--text-mute);
  font-size: 11px;
  margin-top: 2px
}

.risk-metric {
  border-left: 1px solid var(--border-strong);
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 0 22px
}

.risk-metric > span {
  color: var(--text-sub);
  font-size: 11px
}

.risk-metric strong {
  color: var(--text);
  font-size: 25px;
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
  margin-top: 3px
}

.risk-metric small {
  color: var(--text-mute);
  font-size: 11px
}

.risk-metric--danger strong {
  color: var(--danger)
}

.threshold-scale {
  border-left: 1px solid var(--border-strong);
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 0 24px
}

.threshold-scale__head, .threshold-scale__labels {
  align-items: center;
  display: flex;
  justify-content: space-between
}

.threshold-scale__head {
  color: var(--text-sub);
  font-size: 11px
}

.threshold-scale__head strong {
  color: var(--danger);
  font-size: 13px
}

.threshold-scale__track {
  background: var(--border-strong);
  border-radius: 4px;
  height: 6px;
  margin: 10px 0 7px;
  position: relative
}

.threshold-scale__track span {
  background: linear-gradient(90deg, var(--primary), #72a4ff);
  border-radius: 4px;
  display: block;
  height: 100%
}

.threshold-scale__track i {
  background: var(--danger);
  border: 2px solid var(--card);
  border-radius: 50%;
  box-shadow: 0 0 0 2px rgb(196 63 63 / 18%);
  height: 10px;
  position: absolute;
  right: -1px;
  top: -2px;
  width: 10px
}

.threshold-scale__labels {
  color: var(--text-mute);
  font-size: 10px
}

.panel-toolbar {
  align-items: center;
  border-bottom: 1px solid var(--border);
  display: flex;
  gap: 12px;
  justify-content: space-between;
  padding: 16px 18px
}

.filters, .toolbar-actions {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 8px
}

.filters {
  min-width: 0
}

.toolbar-actions {
  flex-shrink: 0
}

.search-input {
  align-items: center;
  background: var(--card);
  border: 1px solid var(--border-strong);
  border-radius: 6px;
  display: flex;
  height: 34px;
  width: 230px
}

.search-input:focus-within, .control:focus, .date-control:focus-within, .score-filter:focus-within {
  border-color: var(--primary);
  box-shadow: 0 0 0 2px var(--primary-soft)
}

.search-input .material-icons-outlined {
  color: var(--text-mute);
  font-size: 17px;
  margin-left: 10px
}

.search-input input {
  background: transparent;
  border: 0;
  color: var(--text);
  font: inherit;
  height: 100%;
  min-width: 0;
  outline: 0;
  padding: 0 9px;
  width: 100%
}

.control {
  background: var(--card);
  border: 1px solid var(--border-strong);
  border-radius: 6px;
  color: var(--text);
  font: inherit;
  height: 34px;
  outline: 0;
  padding: 0 28px 0 10px
}

.date-control, .score-filter {
  align-items: center;
  border: 1px solid var(--border-strong);
  border-radius: 6px;
  color: var(--text-mute);
  display: flex;
  font-size: 11px;
  height: 34px;
  padding-left: 9px
}

.date-control input, .score-filter input {
  background: transparent;
  border: 0;
  color: var(--text-sub);
  font: inherit;
  height: 100%;
  outline: 0;
  padding: 0 7px
}

.date-control input {
  width: 115px
}

.score-filter input {
  width: 70px
}

.button {
  align-items: center;
  border: 1px solid transparent;
  border-radius: 6px;
  cursor: pointer;
  display: inline-flex;
  font: inherit;
  font-size: 12px;
  font-weight: 500;
  gap: 5px;
  height: 34px;
  justify-content: center;
  padding: 0 13px;
  white-space: nowrap
}

.button:disabled {
  cursor: not-allowed;
  opacity: .6
}

.button .material-icons-outlined {
  font-size: 16px
}

.button--primary {
  background: var(--primary);
  color: #fff
}

.button--primary:hover {
  filter: brightness(.96)
}

.button--soft {
  background: var(--primary-soft);
  color: var(--primary)
}

.button--ghost {
  background: var(--card);
  border-color: var(--border-strong);
  color: var(--text-sub)
}

.button--danger-soft {
  background: var(--danger-soft);
  color: var(--danger)
}

.button--release {
  background: var(--success-soft);
  border-color: transparent;
  color: var(--success);
  height: 30px
}

.icon-button, .row-icon {
  align-items: center;
  background: transparent;
  border: 1px solid var(--border-strong);
  border-radius: 6px;
  color: var(--text-sub);
  cursor: pointer;
  display: inline-flex;
  height: 34px;
  justify-content: center;
  width: 34px
}

.icon-button:hover {
  background: var(--bg);
  color: var(--text)
}

.icon-button .material-icons-outlined {
  font-size: 18px
}

.table-wrap {
  overflow-x: auto
}

.table {
  border-collapse: collapse;
  font-size: 12px;
  min-width: 920px;
  width: 100%
}

.table th {
  background: var(--bg);
  border-bottom: 1px solid var(--border);
  color: var(--text-mute);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: .03em;
  padding: 10px 14px;
  text-align: left;
  white-space: nowrap
}

.table td {
  border-bottom: 1px solid var(--border);
  color: var(--text);
  padding: 12px 14px;
  vertical-align: middle
}

.table tbody tr {
  transition: background .15s ease
}

.table tbody tr:hover, .table tbody tr.row--selected {
  background: color-mix(in srgb, var(--primary-soft) 58%, var(--card))
}

.check-cell {
  padding-left: 18px !important;
  padding-right: 4px !important;
  width: 24px
}

.check-cell input {
  accent-color: var(--primary);
  cursor: pointer
}

.actions-cell {
  text-align: right !important;
  white-space: nowrap
}

.vehicle-identity {
  align-items: center;
  background: transparent;
  border: 0;
  color: inherit;
  cursor: pointer;
  display: flex;
  font: inherit;
  gap: 10px;
  padding: 0;
  text-align: left
}

.vehicle-identity--static {
  cursor: default
}

.vehicle-icon {
  align-items: center;
  background: var(--primary-soft);
  border-radius: 7px;
  color: var(--primary);
  display: inline-flex;
  height: 34px;
  justify-content: center;
  width: 34px
}

.vehicle-icon--danger {
  background: var(--danger-soft);
  color: var(--danger)
}

.vehicle-icon .material-icons-outlined {
  font-size: 18px
}

.vehicle-identity > span:last-child, .violation-cell {
  display: grid;
  gap: 2px
}

.vehicle-identity strong {
  font-size: 12px;
  font-weight: 650;
  letter-spacing: .02em
}

.vehicle-identity small, .violation-cell span {
  color: var(--text-mute);
  font-size: 10px
}

.violation-cell strong {
  font-size: 12px;
  font-weight: 550
}

.score-chip {
  align-items: center;
  background: #edf4ff;
  border-radius: 5px;
  color: #3166bb;
  display: inline-flex;
  font-size: 11px;
  font-weight: 700;
  height: 24px;
  justify-content: center;
  min-width: 34px;
  padding: 0 7px
}

.score-chip--medium {
  background: var(--amber-soft);
  color: var(--amber)
}

.score-chip--high {
  background: var(--danger-soft);
  color: var(--danger)
}

.score-chip--large {
  font-size: 15px;
  height: 34px;
  min-width: 64px
}

.text-sub {
  color: var(--text-sub) !important
}

.time-cell {
  color: var(--text-sub) !important;
  font-variant-numeric: tabular-nums;
  white-space: nowrap
}

.status-tag {
  align-items: center;
  border-radius: 12px;
  display: inline-flex;
  font-size: 10px;
  font-weight: 550;
  gap: 5px;
  padding: 4px 8px;
  white-space: nowrap
}

.status-tag i {
  background: currentColor;
  border-radius: 50%;
  height: 5px;
  width: 5px
}

.status-tag--warning {
  background: var(--amber-soft);
  color: var(--amber)
}

.status-tag--danger {
  background: var(--danger-soft);
  color: var(--danger)
}

.status-tag--info {
  background: var(--primary-soft);
  color: var(--primary)
}

.status-tag--success {
  background: var(--success-soft);
  color: var(--success)
}

.status-tag--neutral {
  background: var(--bg);
  color: var(--text-mute)
}

.text-action {
  background: transparent;
  border: 0;
  color: var(--text-sub);
  cursor: pointer;
  font: inherit;
  font-size: 11px;
  padding: 5px
}

.text-action:hover, .text-action--accent {
  color: var(--primary)
}

.row-icon {
  border: 0;
  height: 28px;
  margin-left: 2px;
  width: 28px
}

.row-icon .material-icons-outlined {
  font-size: 16px
}

.row-icon--danger:hover {
  background: var(--danger-soft);
  color: var(--danger)
}

.state {
  align-items: center;
  color: var(--text-mute);
  display: flex;
  gap: 8px;
  justify-content: center;
  min-height: 300px
}

.state--error {
  color: var(--danger)
}

.state--error button {
  background: transparent;
  border: 0;
  color: var(--primary);
  cursor: pointer;
  margin-left: 6px
}

.spinner {
  animation: spin .8s linear infinite;
  border: 2px solid var(--border-strong);
  border-radius: 50%;
  border-top-color: var(--primary);
  display: inline-block;
  height: 16px;
  width: 16px
}

.spinner--button {
  border-color: rgb(255 255 255 / 35%);
  border-top-color: #fff;
  height: 13px;
  width: 13px
}

.empty-state {
  align-items: center;
  color: var(--text-mute);
  display: flex;
  flex-direction: column;
  gap: 4px;
  justify-content: center;
  min-height: 210px
}

.empty-state > .material-icons-outlined {
  color: var(--primary);
  font-size: 32px;
  margin-bottom: 4px;
  opacity: .65
}

.empty-state strong {
  color: var(--text-sub);
  font-size: 13px
}

.empty-state span:last-child {
  font-size: 11px
}

.pagination {
  align-items: center;
  color: var(--text-sub);
  display: flex;
  gap: 4px;
  justify-content: flex-end;
  padding: 12px 16px
}

.pagination__info {
  font-size: 11px;
  margin-right: auto
}

.pagination button {
  align-items: center;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 5px;
  color: var(--text-sub);
  cursor: pointer;
  display: flex;
  height: 28px;
  justify-content: center;
  min-width: 28px
}

.pagination button:hover:not(:disabled), .pagination button.active {
  background: var(--primary-soft);
  color: var(--primary)
}

.pagination button:disabled {
  cursor: not-allowed;
  opacity: .35
}

.pagination .material-icons-outlined {
  font-size: 17px
}

.rule-overview {
  align-items: center;
  background: linear-gradient(120deg, #f3f7ff 0%, var(--card) 68%);
  border-bottom: 1px solid var(--border);
  display: grid;
  gap: 28px;
  grid-template-columns:minmax(300px, 1fr) minmax(320px, 1.15fr) auto;
  padding: 26px 24px
}

.rule-overview__copy {
  align-items: center;
  display: flex;
  gap: 14px
}

.rule-overview__icon {
  align-items: center;
  background: var(--primary);
  border-radius: 9px;
  color: #fff;
  display: flex;
  height: 44px;
  justify-content: center;
  width: 44px
}

.rule-overview__icon .material-icons-outlined {
  font-size: 22px
}

.section-kicker {
  color: var(--text-mute);
  font-size: 10px;
  font-weight: 650;
  letter-spacing: .1em;
  text-transform: uppercase
}

.rule-overview h2 {
  color: var(--text);
  font-size: 16px;
  font-weight: 550;
  margin: 2px 0 3px
}

.rule-overview h2 strong {
  color: var(--primary);
  font-size: 24px
}

.rule-overview p {
  color: var(--text-sub);
  font-size: 11px;
  margin: 0
}

.score-ruler {
  border-top: 2px solid var(--border-strong);
  display: flex;
  justify-content: space-between;
  margin-top: 12px;
  position: relative
}

.score-ruler::before {
  background: var(--primary);
  content: "";
  height: 2px;
  left: 0;
  position: absolute;
  top: -2px;
  width: 70%
}

.score-ruler span {
  color: var(--text-mute);
  font-size: 9px;
  padding-top: 10px;
  position: relative
}

.score-ruler i {
  background: var(--border-strong);
  height: 7px;
  left: 50%;
  position: absolute;
  top: -2px;
  width: 1px
}

.score-ruler__tick--major {
  color: var(--danger) !important;
  font-weight: 700
}

.score-ruler__tick--major i {
  background: var(--danger);
  height: 10px;
  width: 2px
}

.section-head {
  align-items: center;
  border-bottom: 1px solid var(--border);
  display: flex;
  justify-content: space-between;
  padding: 18px
}

.section-head h2 {
  font-size: 14px;
  font-weight: 600;
  margin: 0
}

.section-head p {
  color: var(--text-mute);
  font-size: 11px;
  margin: 3px 0 0
}

.rules-table {
  min-width: 720px
}

.order-number {
  color: var(--text-mute) !important;
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace
}

.rule-description {
  color: var(--text-sub) !important;
  max-width: 420px
}

.penalty-notice {
  align-items: center;
  background: linear-gradient(90deg, var(--danger-soft), var(--card));
  border-bottom: 1px solid var(--border);
  display: flex;
  gap: 13px;
  padding: 19px 22px
}

.penalty-notice__mark {
  align-items: center;
  background: var(--danger);
  border-radius: 8px;
  color: #fff;
  display: flex;
  height: 38px;
  justify-content: center;
  width: 38px
}

.penalty-notice__mark .material-icons-outlined {
  font-size: 20px
}

.penalty-notice > div {
  display: grid;
  gap: 3px
}

.penalty-notice > div strong {
  font-size: 13px
}

.penalty-notice > div span {
  color: var(--text-sub);
  font-size: 11px
}

.penalty-notice__count {
  color: var(--danger);
  font-size: 25px;
  font-weight: 680;
  margin-left: auto;
  text-align: right
}

.penalty-notice__count small {
  color: var(--text-mute);
  display: block;
  font-size: 10px;
  font-weight: 500
}

.score-progress {
  display: grid;
  gap: 6px;
  min-width: 130px;
  width: 70%
}

.score-progress > div {
  align-items: baseline;
  display: flex;
  gap: 3px
}

.score-progress strong {
  font-size: 16px
}

.score-progress span {
  color: var(--text-mute);
  font-size: 10px
}

.score-progress__track {
  background: var(--border-strong);
  border-radius: 4px;
  height: 4px;
  overflow: hidden
}

.score-progress__track i {
  background: linear-gradient(90deg, var(--amber), var(--danger));
  display: block;
  height: 100%
}

.released-text {
  color: var(--text-mute);
  font-size: 11px
}

.drawer-mask, .modal-mask {
  background: rgb(15 23 42 / 36%);
  inset: 0;
  position: fixed;
  z-index: 100
}

.drawer-mask {
  display: flex;
  justify-content: flex-end
}

.drawer {
  animation: drawer-in .24s ease-out;
  background: var(--card);
  box-shadow: -18px 0 50px rgb(15 23 42 / 15%);
  display: flex;
  flex-direction: column;
  height: 100%;
  max-width: 100%;
  width: 440px
}

.drawer__head, .modal__head {
  align-items: center;
  border-bottom: 1px solid var(--border);
  display: flex;
  justify-content: space-between;
  padding: 20px 22px
}

.drawer__head h2, .modal__head h2 {
  font-size: 18px;
  font-weight: 650;
  margin: 3px 0 0
}

.drawer__body {
  flex: 1;
  overflow-y: auto;
  padding: 20px 22px
}

.drawer__foot, .modal__foot {
  align-items: center;
  border-top: 1px solid var(--border);
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  padding: 14px 22px
}

.evidence {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 8px;
  height: 210px;
  overflow: hidden;
  position: relative
}

.evidence img {
  height: 100%;
  object-fit: cover;
  width: 100%
}

.evidence > span {
  background: rgb(15 23 42 / 72%);
  border-radius: 4px;
  bottom: 10px;
  color: #fff;
  font-size: 10px;
  left: 10px;
  padding: 4px 7px;
  position: absolute
}

.evidence--empty {
  align-items: center;
  color: var(--text-mute);
  display: flex;
  flex-direction: column;
  gap: 4px;
  justify-content: center;
  text-align: center
}

.evidence--empty .material-icons-outlined {
  font-size: 34px;
  opacity: .55
}

.evidence--empty strong {
  color: var(--text-sub);
  font-size: 12px
}

.evidence--empty small {
  font-size: 10px;
  max-width: 80%;
  word-break: break-all
}

.detail-hero {
  align-items: center;
  border-bottom: 1px solid var(--border);
  display: flex;
  justify-content: space-between;
  padding: 20px 2px 16px
}

.detail-hero > div {
  display: grid;
  gap: 3px
}

.detail-hero span:first-child, .detail-note > span {
  color: var(--text-mute);
  font-size: 10px
}

.detail-hero strong {
  font-size: 16px
}

.detail-list {
  margin: 0;
  padding: 8px 0
}

.detail-list > div {
  align-items: center;
  border-bottom: 1px dashed var(--border);
  display: grid;
  grid-template-columns:95px 1fr;
  min-height: 46px
}

.detail-list dt {
  color: var(--text-mute);
  font-size: 11px
}

.detail-list dd {
  color: var(--text-sub);
  font-size: 12px;
  margin: 0;
  text-align: right
}

.detail-note {
  background: var(--bg);
  border-radius: 7px;
  margin-top: 12px;
  padding: 12px
}

.detail-note p {
  color: var(--text-sub);
  font-size: 12px;
  line-height: 1.7;
  margin: 5px 0 0
}

.modal-mask {
  align-items: center;
  display: flex;
  justify-content: center;
  padding: 20px
}

.modal {
  background: var(--card);
  border: 1px solid var(--border-strong);
  border-radius: 10px;
  box-shadow: 0 24px 70px rgb(15 23 42 / 24%);
  max-height: calc(100dvh - 40px);
  max-width: 100%;
  overflow: auto;
  width: 620px
}

.modal--compact {
  width: 480px
}

.modal__body {
  padding: 20px 22px
}

.form-grid {
  display: grid;
  gap: 16px;
  grid-template-columns:1fr 1fr
}

.field {
  display: grid;
  gap: 7px
}

.field--wide {
  grid-column: 1/-1
}

.field > span {
  color: var(--text-sub);
  font-size: 11px;
  font-weight: 550
}

.field input, .field select, .field textarea, .number-suffix {
  background: var(--card);
  border: 1px solid var(--border-strong);
  border-radius: 6px;
  color: var(--text);
  font: inherit;
  outline: 0;
  width: 100%
}

.field input, .field select {
  box-sizing: border-box;
  height: 36px;
  padding: 0 10px
}

.field textarea {
  box-sizing: border-box;
  padding: 9px 10px;
  resize: vertical
}

.field input:focus, .field select:focus, .field textarea:focus, .number-suffix:focus-within {
  border-color: var(--primary);
  box-shadow: 0 0 0 2px var(--primary-soft)
}

.form-error {
  background: var(--danger-soft);
  border-radius: 6px;
  color: var(--danger);
  font-size: 11px;
  margin: 14px 0 0;
  padding: 9px 11px
}

.decision-cards {
  display: grid;
  gap: 10px;
  grid-template-columns:1fr 1fr;
  margin-bottom: 18px
}

.decision-cards label {
  border: 1px solid var(--border-strong);
  border-radius: 7px;
  cursor: pointer;
  display: grid;
  gap: 3px;
  padding: 13px;
  position: relative
}

.decision-cards label.selected {
  background: var(--primary-soft);
  border-color: var(--primary)
}

.decision-cards input {
  opacity: 0;
  position: absolute
}

.decision-cards .material-icons-outlined {
  color: var(--text-mute);
  font-size: 20px
}

.decision-cards label.selected .material-icons-outlined {
  color: var(--primary)
}

.decision-cards strong {
  font-size: 12px
}

.decision-cards small {
  color: var(--text-mute);
  font-size: 10px
}

.modal-hint {
  background: var(--primary-soft);
  border-radius: 6px;
  color: var(--text-sub);
  font-size: 11px;
  line-height: 1.6;
  margin: 0 0 18px;
  padding: 10px 12px
}

.number-suffix {
  align-items: center;
  box-sizing: border-box;
  display: flex;
  overflow: hidden
}

.number-suffix input {
  border: 0 !important;
  box-shadow: none !important
}

.number-suffix em {
  color: var(--text-mute);
  font-size: 11px;
  font-style: normal;
  padding-right: 10px
}

.release-warning {
  align-items: flex-start;
  background: var(--amber-soft);
  border-radius: 7px;
  color: var(--amber);
  display: flex;
  gap: 10px;
  margin-bottom: 18px;
  padding: 13px
}

.release-warning > .material-icons-outlined {
  font-size: 20px
}

.release-warning strong {
  font-size: 12px
}

.release-warning p {
  color: var(--text-sub);
  font-size: 11px;
  line-height: 1.6;
  margin: 3px 0 0
}

@keyframes spin {
  to {
    transform: rotate(360deg)
  }
}

@keyframes drawer-in {
  from {
    opacity: .55;
    transform: translateX(40px)
  }
  to {
    opacity: 1;
    transform: translateX(0)
  }
}

@media (max-width: 1150px) {
  .risk-ribbon {
    grid-template-columns:150px repeat(3, 1fr)
  }

  .threshold-scale {
    border-left: 0;
    border-top: 1px solid var(--border-strong);
    grid-column: 1/-1;
    padding: 14px 10px
  }

  .rule-overview {
    grid-template-columns:1fr auto
  }

  .score-ruler {
    grid-column: 1/-1;
    grid-row: 2
  }
}

@media (max-width: 820px) {
  .violation-page {
    padding: 16px
  }

  .page__header {
    align-items: flex-start
  }

  .header-actions {
    width: 100%
  }

  .header-actions .button {
    flex: 1
  }

  .tabs {
    overflow-x: auto;
    padding: 0 8px
  }

  .tab {
    padding: 0 12px;
    white-space: nowrap
  }

  .risk-ribbon {
    grid-template-columns:repeat(3, 1fr);
    padding: 0
  }

  .risk-ribbon__intro {
    display: none
  }

  .risk-metric {
    padding: 18px 14px
  }

  .panel-toolbar {
    align-items: stretch;
    flex-direction: column
  }

  .filters {
    align-items: stretch
  }

  .search-input {
    width: 100%
  }

  .toolbar-actions {
    justify-content: flex-end
  }

  .rule-overview {
    align-items: flex-start;
    grid-template-columns:1fr;
    padding: 20px
  }

  .rule-overview > .button {
    justify-self: start
  }

  .score-ruler {
    grid-column: auto;
    grid-row: auto
  }

  .penalty-notice {
    align-items: flex-start
  }

  .form-grid {
    grid-template-columns:1fr
  }

  .field--wide {
    grid-column: auto
  }
}

@media (max-width: 560px) {
  .risk-ribbon {
    grid-template-columns:1fr 1fr
  }

  .risk-metric:nth-of-type(3) {
    grid-column: 1/-1
  }

  .threshold-scale {
    grid-column: 1/-1
  }

  .date-control, .control {
    flex: 1;
    min-width: 135px
  }

  .drawer {
    width: 100%
  }

  .modal-mask {
    padding: 10px
  }

  .modal {
    max-height: calc(100dvh - 20px)
  }

  .decision-cards {
    grid-template-columns:1fr
  }

  .penalty-notice__count {
    display: none
  }
}

@media (prefers-reduced-motion: reduce) {
  .drawer, .spinner {
    animation: none
  }

  .table tbody tr {
    transition: none
  }
}

:global([data-theme="dark"]) .violation-page {
  --amber: #f3ae5e;
  --amber-soft: #38291a;
  --danger: #ff8181;
  --danger-soft: #3b2023;
  --success: #68d4a6;
  --success-soft: #15352a
}

:global([data-theme="dark"]) .rule-overview {
  background: linear-gradient(120deg, #1e293b, var(--card))
}
</style>
