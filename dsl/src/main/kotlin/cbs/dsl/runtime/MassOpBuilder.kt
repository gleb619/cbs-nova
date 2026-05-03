package cbs.dsl.runtime

import cbs.dsl.api.LockDefinition
import cbs.dsl.api.MassOperationDefinition
import cbs.dsl.api.ParameterDefinition
import cbs.dsl.api.SignalTypes.Signal
import cbs.dsl.api.SourceDefinition
import cbs.dsl.api.TriggerDefinition
import cbs.dsl.api.context.MassOperationContext

class MassOpBuilder(override val code: String) : MassOperationDefinition {
  private val _parameters = mutableListOf<ParameterDefinition>()
  override val parameters: List<ParameterDefinition>
    get() = _parameters.toList()

  private var _category: String = ""
  override val category: String
    get() = _category

  private val _triggers = mutableListOf<TriggerDefinition>()
  override val triggers: List<TriggerDefinition>
    get() = _triggers.toList()

  private var _source: SourceDefinition? = null
  override val source: SourceDefinition
    get() = _source ?: error("MassOperation '$code' has no source defined")

  private var _lock: LockDefinition? = null
  override val lock: LockDefinition?
    get() = _lock

  private var _contextBlock: (MassOperationContext) -> Unit = {}
  override val contextBlock: (MassOperationContext) -> Unit
    get() = _contextBlock

  private var _item: ((MassOperationContext) -> Unit)? = null
  override val itemBlock: (MassOperationContext) -> Unit
    get() = _item ?: error("MassOperation '$code' has no item block defined")

  private var _onPartial: ((Signal) -> Unit)? = null
  override val onPartial: ((Signal) -> Unit)?
    get() = _onPartial

  private var _onCompleted: ((Signal) -> Unit)? = null
  override val onCompleted: ((Signal) -> Unit)?
    get() = _onCompleted

  fun category(c: String) {
    _category = c
  }

  fun parameters(block: ParametersScope.() -> Unit) {
    _parameters += ParametersScope().apply(block).definitions
  }

  fun trigger(t: TriggerDefinition) {
    _triggers += t
  }

  fun source(s: SourceDefinition) {
    _source = s
  }

  fun source(block: (MassOperationContext) -> List<Map<String, Any>>) {
    _source =
        object : SourceDefinition {
          override fun load(ctx: MassOperationContext) = block(ctx)
        }
  }

  fun lock(l: LockDefinition) {
    _lock = l
  }

  fun context(block: (MassOperationContext) -> Unit) {
    _contextBlock = block
  }

  fun item(block: (MassOperationContext) -> Unit) {
    _item = block
  }

  fun onPartial(block: (Signal) -> Unit) {
    _onPartial = block
  }

  fun onCompleted(block: (Signal) -> Unit) {
    _onCompleted = block
  }
}

fun massOperation(code: String, block: MassOpBuilder.() -> Unit): MassOperationDefinition =
    MassOpBuilder(code).apply(block)
