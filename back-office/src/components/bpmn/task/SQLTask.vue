<template>
  <div>
    <geometry-element
      selectable
      movable
      resizable
      connectable
      deletable
      :id.sync="activity.tracingTag"
      :x.sync="activity.elementView.x"
      :y.sync="activity.elementView.y"
      :width.sync="activity.elementView.width"
      :height.sync="activity.elementView.height"
      :_style.sync="style"
      :parentId.sync="activity.elementView.parent"
      :label.sync="activity.name.text"
      v-on:dblclick="showProperty"
      v-on:selectShape="closeComponentChanger(); selectedActivity();"
      v-on:deSelectShape="closeComponentChanger(); deSelectedActivity();"
      v-on:removeShape="closeComponentChanger"
      v-on:redrawShape="closeComponentChanger"
      v-on:addedToGroup="onAddedToGroup"
    >
      <geometry-rect
        :_style="{
          'fill-r': 1,
          'fill-cx': .1,
          'fill-cy': .1,
          'stroke-width': 1.2,
          fill: '#FFFFFF',
          'fill-opacity': 0,
          r: '10'
        }"
      >
      </geometry-rect>

      <sub-elements>
        <image-element
          v-bind:image="sql_image"
          :sub-width="'20px'"
          :sub-height="'20px'"
          :sub-top="'5px'"
          :sub-left="'5px'"
        >
        </image-element>

        <bpmn-loop-type :loopType="loopType"></bpmn-loop-type>
        <bpmn-state-animation :status="status" :type="type"></bpmn-state-animation>
      </sub-elements>
      <bpmn-sub-controller :type="type"></bpmn-sub-controller>
    </geometry-element>

    <bpmn-property-panel
      :drawer.sync="drawer"
      :item.sync="activity"
    >
      <template slot="properties-contents">
        <md-input-container>
          <label>액티비티 명</label>
          <md-input type="text" v-model="activity.name.text"></md-input>
        </md-input-container>

        <!-- ============ 접속할 데이터베이스 ============ -->
        <md-input-container>
          <label>데이터베이스 연결 방식</label>
          <md-select v-model="connectionType" @change="changeConnectionType">
            <md-option value="dataSource">서버에 설정된 DataSource</md-option>
            <md-option value="jdbc">JDBC 직접 접속</md-option>
          </md-select>
        </md-input-container>

        <md-input-container v-if="connectionType == 'dataSource'">
          <label>DataSource 이름 (비우면 기본 DataSource)</label>
          <md-input type="text" v-model="activity.connectionFactory.dataSourceName"></md-input>
        </md-input-container>

        <template v-if="connectionType == 'jdbc'">
          <md-input-container>
            <label>드라이버 클래스</label>
            <md-input type="text" v-model="activity.connectionFactory.driverClass"></md-input>
          </md-input-container>
          <md-input-container>
            <label>JDBC URL (${key} 로 서버 설정값 참조 가능)</label>
            <md-input type="text" v-model="activity.connectionFactory.connectionString"></md-input>
          </md-input-container>
          <md-input-container>
            <label>사용자</label>
            <md-input type="text" v-model="activity.connectionFactory.userId"></md-input>
          </md-input-container>
          <md-input-container>
            <label>비밀번호</label>
            <md-input type="password" v-model="activity.connectionFactory.password"></md-input>
          </md-input-container>
        </template>

        <!-- ============ 설정 방식 (strategy) ============ -->
        <md-input-container>
          <label>설정 방식</label>
          <md-select v-model="strategyType" @change="changeStrategyType">
            <md-option value="direct">SQL 직접 작성</md-option>
            <md-option value="mapping">데이터베이스 매핑 (SQL 자동생성)</md-option>
          </md-select>
        </md-input-container>

        <!-- ---- SQL 직접 작성 ---- -->
        <template v-if="strategyType == 'direct'">
          <md-input-container>
            <label>SQL (파라미터는 ?, 값 치환은 &lt;%변수명%&gt;)</label>
            <md-textarea v-model="activity.sqlStmt"></md-textarea>
          </md-input-container>

          <md-checkbox v-model="activity.query">조회(SELECT) 문이다</md-checkbox>

          <p>입력 파라미터 (SQL 의 ? 순서대로)</p>
          <bpmn-parameter-contexts
            :parameter-contexts="activity.parameters"
            :definition="definition"
            :label-for-argument="'컬럼(설명)'"
          ></bpmn-parameter-contexts>

          <template v-if="activity.query">
            <p>조회 결과 매핑 (조회 컬럼 → 프로세스 변수)</p>
            <bpmn-parameter-contexts
              :parameter-contexts="activity.selectMappings"
              :definition="definition"
              :label-for-argument="'조회 컬럼명'"
            ></bpmn-parameter-contexts>
          </template>
        </template>

        <!-- ---- 데이터베이스 매핑 ---- -->
        <template v-if="strategyType == 'mapping'">
          <md-input-container>
            <label>수행 방식</label>
            <md-select v-model="activity.strategy.queryMode">
              <md-option value="SELECT">SELECT (조회하여 변수에 담기)</md-option>
              <md-option value="INSERT">INSERT (변수값으로 등록)</md-option>
              <md-option value="UPDATE">UPDATE (키 기준 수정)</md-option>
              <md-option value="DELETE">DELETE (키 기준 삭제)</md-option>
            </md-select>
          </md-input-container>

          <p>컬럼 매핑 (테이블.컬럼 ↔ 프로세스 변수)</p>
          <div v-for="mappingElement in activity.strategy.mappingContext.mappingElements" style="height: 80px;">
            <div style="width: 30%; float: left;">
              <md-input-container>
                <label>테이블.컬럼</label>
                <md-input v-model="mappingElement.argument.text"></md-input>
              </md-input-container>
            </div>
            <div style="width: 40%; float: left;">
              <bpmn-variable-selector
                v-model="mappingElement.variable"
                :definition="definition"
              ></bpmn-variable-selector>
            </div>
            <md-layout style="width: 15%; float: left;">
              <md-checkbox v-model="mappingElement.isKey">키</md-checkbox>
            </md-layout>
            <div style="width: 10%; float: left; margin-top: 20px; margin-left: 10px;">
              <md-icon v-on:click.native="removeMappingElement(mappingElement)"
                       class="md-primary"
                       style="cursor: pointer">delete</md-icon>
            </div>
          </div>
          <md-button v-on:click.native="addMappingElement">매핑 추가</md-button>

          <p class="hint">
            키로 지정한 컬럼은 WHERE 절이 되고, 나머지 컬럼이 SELECT / INSERT / UPDATE 대상이 됩니다.
          </p>
        </template>

        <md-checkbox v-model="activity.applySingleValueOnly">복수값이어도 한 건만 처리</md-checkbox>
        <md-checkbox v-model="activity.replaceWithBlankStringIfNull">NULL 은 빈 문자열로 처리</md-checkbox>
      </template>
      <template slot="additional-tabs">

      </template>
    </bpmn-property-panel>
  </div>
</template>

<script>
  import IBpmn from '../IBpmn'

  const JDBC_CONNECTION_FACTORY = 'org.uengine.util.dao.JDBCConnectionFactory';
  const DATASOURCE_CONNECTION_FACTORY = 'org.uengine.util.dao.DataSourceConnectionFactory';
  const DATABASE_MAPPING_STRATEGY = 'org.uengine.kernel.bpmn.sql.DatabaseMappingStrategy';
  const LEGACY_QUERY_MODES = {1: 'SELECT', 2: 'INSERT', 3: 'UPDATE', 4: 'DELETE'};

  export default {
    mixins: [IBpmn],
    name: 'bpmn-sql-task',
    props: {},
    created: function () {
      // 예전 정의(연결정보/파라미터 없이 저장된 것)도 편집 가능하도록 보정한다.
      if (!this.activity.connectionFactory) {
        this.$set(this.activity, 'connectionFactory', {_type: DATASOURCE_CONNECTION_FACTORY, dataSourceName: ''});
      }
      if (!this.activity.parameters) {
        this.$set(this.activity, 'parameters', []);
      }
      if (!this.activity.selectMappings) {
        this.$set(this.activity, 'selectMappings', []);
      }
      if (this.activity.strategy === undefined) {
        this.$set(this.activity, 'strategy', null);
      }
      if (this.activity.strategy && this.activity.strategy._type == DATABASE_MAPPING_STRATEGY) {
        if (!this.activity.strategy.mappingContext) {
          this.$set(this.activity.strategy, 'mappingContext', {mappingElements: []});
        }
        if (!this.activity.strategy.mappingContext.mappingElements) {
          this.$set(this.activity.strategy.mappingContext, 'mappingElements', []);
        }
        // 예전 정의는 queryMode 를 정수(1~4)로 갖고 있다.
        this.$set(this.activity.strategy, 'queryMode',
          LEGACY_QUERY_MODES[this.activity.strategy.queryMode] || this.activity.strategy.queryMode || 'INSERT');
      }
    },
    computed: {
      defaultStyle() {
        return {}
      },
      type() {
        return 'Task'
      },
      className() {
        return 'org.uengine.kernel.bpmn.SQLTask'
      },
      createNew(newTracingTag, x, y, width, height) {
        return {
          _type: this.className(),
          name: {
            text: ''
          },
          connectionFactory: {
            _type: DATASOURCE_CONNECTION_FACTORY,
            dataSourceName: ''
          },
          sqlStmt: '',
          parameters: [],
          selectMappings: [],
          query: false,
          replaceWithBlankStringIfNull: true,
          tracingTag: newTracingTag,
          selected: false,
          elementView: {
            '_type': 'org.uengine.kernel.view.DefaultActivityView',
            'id': newTracingTag,
            'x': x,
            'y': y,
            'width': width,
            'height': height,
            'style': JSON.stringify({})
          }
        }
      }
    },
    data: function () {
      return {
        connectionType:
          (this.activity.connectionFactory && this.activity.connectionFactory._type == JDBC_CONNECTION_FACTORY)
            ? 'jdbc' : 'dataSource',
        strategyType:
          (this.activity.strategy && this.activity.strategy._type == DATABASE_MAPPING_STRATEGY)
            ? 'mapping' : 'direct',
        sql_image: location.pathname + ((location.pathname == '/' || location.pathname.lastIndexOf('/') > 0) ? '' : '/') + 'static/image/symbol/data_store.png'
      };
    },
    methods: {
      changeConnectionType: function () {
        if (this.connectionType == 'jdbc') {
          this.$set(this.activity, 'connectionFactory', {
            _type: JDBC_CONNECTION_FACTORY,
            driverClass: '',
            connectionString: '',
            userId: '',
            password: ''
          });
        } else {
          this.$set(this.activity, 'connectionFactory', {
            _type: DATASOURCE_CONNECTION_FACTORY,
            dataSourceName: ''
          });
        }
      },

      changeStrategyType: function () {
        if (this.strategyType == 'mapping') {
          this.$set(this.activity, 'strategy', {
            _type: DATABASE_MAPPING_STRATEGY,
            queryMode: 'INSERT',
            mappingContext: {
              mappingElements: []
            }
          });
        } else {
          // strategy 가 없으면 서버는 DirectSQLStrategy 로 동작한다.
          this.$set(this.activity, 'strategy', null);
        }
      },

      addMappingElement: function () {
        this.activity.strategy.mappingContext.mappingElements.push({
          argument: {text: 'TABLE.COLUMN'},
          variable: {name: ''},
          isKey: false
        });
      },

      removeMappingElement: function (mappingElement) {
        var mappingElements = this.activity.strategy.mappingContext.mappingElements;
        mappingElements.splice(mappingElements.indexOf(mappingElement), 1);
      }
    }
  }
</script>


<style scoped lang="scss" rel="stylesheet/scss">
  .hint {
    font-size: 12px;
    color: #888;
  }
</style>
