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

class MassOpBuilder(val massOpCode: String) : MassOperationDefinition {
  private val _parameters = mutableListOf<ParameterDefinition>()
  override fun getParameters(): List<ParameterDefinition> = _parameters.toList()

  private var _category: String = ""
  override fun getCategory(): String = _category

  private val _triggers = mutableListOf<TriggerDefinition>()
  override fun getTriggers(): List<TriggerDefinition> = _triggers.toList()

  private var _source: SourceDefinition? = null
  override fun getSource(): SourceDefinition = _source ?: error("MassOperation '$massOpCode' has no source defined")

  private var _lock: LockDefinition? = null
  override fun getLock(): LockDefinition? = _lock

  private var _contextBlock: Consumer<MassOperationContext> = Consumer { }
  override fun getContextBlock(): Consumer<MassOperationContext> = _contextBlock

  private var _item: Consumer<MassOperationContext>? = null
  override fun getItemBlock(): Consumer<MassOperationContext> = _item ?: error("MassOperation '$massOpCode' has no item block defined")

  private var _onPartial: Consumer<Signal>? = null
  override fun getOnPartial(): Consumer<Signal>? = _onPartial

  private var _onCompleted: Consumer<Signal>? = null
  override fun getOnCompleted(): Consumer<Signal>? = _onCompleted

  override fun getCode(): String = massOpCode

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
