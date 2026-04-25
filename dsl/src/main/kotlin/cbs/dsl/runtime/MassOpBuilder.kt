package cbs.dsl.runtime

import cbs.dsl.api.LockDefinition
import cbs.dsl.api.MassOperationDefinition
import cbs.dsl.api.MassOperationTypes.MassOperationInput
import cbs.dsl.api.MassOperationTypes.MassOperationOutput
import cbs.dsl.api.ParameterDefinition
import cbs.dsl.api.SignalTypes.Signal
import cbs.dsl.api.SourceDefinition
import cbs.dsl.api.TriggerDefinition
import cbs.dsl.api.context.MassOperationContext
import java.util.function.Consumer

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

  private var _contextBlock: Consumer<MassOperationContext> = Consumer { }
  override val contextBlock: Consumer<MassOperationContext>
    get() = _contextBlock

  private var _item: Consumer<MassOperationContext>? = null
  override val itemBlock: Consumer<MassOperationContext>
    get() = _item ?: error("MassOperation '$code' has no item block defined")

  private var _onPartial: Consumer<Signal>? = null
  override val onPartial: Consumer<Signal>?
    get() = _onPartial

  private var _onCompleted: Consumer<Signal>? = null
  override val onCompleted: Consumer<Signal>?
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
    _contextBlock = Consumer { block(it) }
  }

  fun item(block: (MassOperationContext) -> Unit) {
    _item = Consumer { block(it) }
  }

  fun onPartial(block: (Signal) -> Unit) {
    _onPartial = Consumer { block(it) }
  }

  fun onCompleted(block: (Signal) -> Unit) {
    _onCompleted = Consumer { block(it) }
  }

  override fun execute(input: MassOperationInput): MassOperationOutput {
    val ctx = MassOperationContext.builder()
      .performedBy("")
      .dslVersion("")
      .build()
    _contextBlock.accept(ctx)

    val items = source.load(ctx)
    var processed = 0
    var failed = 0

    items.forEach { item ->
      try {
        ctx["item"] = item
        _item!!.accept(ctx)
        processed++
      } catch (e: Exception) {
        failed++
      }
    }

    return MassOperationOutput(processed, failed, "COMPLETED")
  }
}

fun massOperation(code: String, block: MassOpBuilder.() -> Unit): MassOperationDefinition =
    MassOpBuilder(code).apply(block)
