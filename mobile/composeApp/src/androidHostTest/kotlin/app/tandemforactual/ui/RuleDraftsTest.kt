package app.tandemforactual.ui

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RuleDraftsTest {
    private val format: (Long) -> String = { (it / 100.0).toString() }
    private val parse: (String) -> Long? = { text -> text.toDoubleOrNull()?.let { (it * 100).toLong() } }

    private fun rule(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    @Test
    fun readsSupportedActualRules() {
        val draft =
            rule(
                """
                {"stage":"pre","conditionsOp":"or",
                 "conditions":[
                   {"field":"payee","op":"oneOf","value":["p1","p2"]},
                   {"field":"imported_payee","op":"contains","value":"COFFEE"},
                   {"field":"amount","op":"isbetween","value":{"num1":500,"num2":1500},"options":{"outflow":true}},
                   {"field":"amount","op":"gt","value":-2500}
                 ],
                 "actions":[
                   {"field":"category","op":"set","value":"c1"},
                   {"field":"cleared","op":"set","value":false},
                   {"op":"append-notes","value":" #coffee"}
                 ]}
                """,
            ).toRuleDraftOrNull(format)

        assertEquals(
            RuleDraft(
                stage = RuleStage.PRE,
                matchAll = false,
                conditions =
                    listOf(
                        RuleConditionDraft(RuleConditionField.PAYEE, "oneOf", ids = listOf("p1", "p2")),
                        RuleConditionDraft(RuleConditionField.IMPORTED_PAYEE, "contains", text = "COFFEE"),
                        RuleConditionDraft(RuleConditionField.MONEY_OUT, "isbetween", amountText = "5.0", amountMaxText = "15.0"),
                        RuleConditionDraft(RuleConditionField.AMOUNT, "gt", amountText = "-25.0"),
                    ),
                actions =
                    listOf(
                        RuleActionDraft(RuleActionKind.SET_CATEGORY, id = "c1"),
                        RuleActionDraft(RuleActionKind.SET_CLEARED, cleared = false),
                        RuleActionDraft(RuleActionKind.APPEND_NOTES, text = " #coffee"),
                    ),
            ),
            draft,
        )
    }

    @Test
    fun leavesRulesWithUnsupportedPartsReadOnly() {
        val unsupported =
            listOf(
                """{"conditions":[{"field":"date","op":"is","value":"2026-11-01"}],"actions":[{"field":"notes","op":"set","value":"x"}]}""",
                """{"conditions":[{"field":"notes","op":"hasTags","value":"#a"}],"actions":[{"field":"notes","op":"set","value":"x"}]}""",
                """{"conditions":[{"field":"notes","op":"is","value":"x"}],"actions":[{"op":"set-split-amount","value":50}]}""",
                """{"conditions":[{"field":"notes","op":"is","value":"x"}],"actions":[{"op":"link-schedule","value":"s1"}]}""",
                """{"conditions":[{"field":"notes","op":"is","value":"x"}],"actions":[{"field":"notes","op":"set","value":"x","options":{"template":"{{a}}"}}]}""",
                """{"conditions":[{"field":"amount","op":"is","value":5,"options":{"inflow":true,"outflow":true}}],"actions":[{"field":"notes","op":"set","value":"x"}]}""",
                """{"conditions":[{"field":"date","op":"is","value":"x","options":{"month":true}}],"actions":[{"field":"notes","op":"set","value":"x"}]}""",
                """{"stage":"later","conditions":[{"field":"notes","op":"is","value":"x"}],"actions":[{"field":"notes","op":"set","value":"x"}]}""",
                """{"conditions":[],"actions":[{"field":"notes","op":"set","value":"x"}]}""",
            )
        unsupported.forEach { assertNull(rule(it).toRuleDraftOrNull(format), it) }
    }

    @Test
    fun buildsTheRequestWithMoneyDirectionAndPositiveAmounts() {
        val body =
            RuleDraft(
                stage = RuleStage.POST,
                conditions =
                    listOf(
                        RuleConditionDraft(RuleConditionField.MONEY_OUT, "lt", amountText = "-12.5"),
                        RuleConditionDraft(RuleConditionField.MONEY_IN, "isbetween", amountText = "1", amountMaxText = "2"),
                        RuleConditionDraft(RuleConditionField.ACCOUNT, "is", ids = listOf("a1")),
                        RuleConditionDraft(RuleConditionField.CATEGORY, "notOneOf", ids = listOf("c1", "c2")),
                        RuleConditionDraft(RuleConditionField.NOTES, "matches", text = "^rent"),
                    ),
                actions =
                    listOf(
                        RuleActionDraft(RuleActionKind.SET_PAYEE, id = "p1"),
                        RuleActionDraft(RuleActionKind.SET_CLEARED, cleared = true),
                        RuleActionDraft(RuleActionKind.PREPEND_NOTES, text = "Rent: "),
                    ),
            ).toRequestBody(parse).getOrThrow()

        assertEquals(
            rule(
                """
                {"stage":"post","conditionsOp":"and",
                 "conditions":[
                   {"field":"amount","op":"lt","amountMinor":1250,"outflow":true},
                   {"field":"amount","op":"isbetween","range":{"num1":100,"num2":200},"inflow":true},
                   {"field":"account","op":"is","text":"a1"},
                   {"field":"category","op":"notOneOf","ids":["c1","c2"]},
                   {"field":"notes","op":"matches","text":"^rent"}
                 ],
                 "actions":[
                   {"field":"payee","op":"set","text":"p1"},
                   {"field":"cleared","op":"set","cleared":true},
                   {"op":"prepend-notes","text":"Rent: "}
                 ]}
                """,
            ),
            body,
        )
    }

    @Test
    fun explainsWhatStillNeedsFillingIn() {
        fun problem(draft: RuleDraft) = draft.toRequestBody(parse).exceptionOrNull()?.message

        assertEquals("Add at least one condition.", problem(RuleDraft(conditions = emptyList())))
        assertEquals("Add at least one action.", problem(RuleDraft(actions = emptyList())))
        assertEquals("Choose a payee for this condition.", problem(RuleDraft()))
        val filled = listOf(RuleConditionDraft(RuleConditionField.NOTES, "contains", text = "rent"))
        assertEquals("Choose a value for “Set category”.", problem(RuleDraft(conditions = filled)))
        assertEquals(
            "Enter the lower amount first for “Amount”.",
            problem(
                RuleDraft(
                    conditions = listOf(RuleConditionDraft(RuleConditionField.AMOUNT, "isbetween", amountText = "5", amountMaxText = "1")),
                ),
            ),
        )
        assertEquals(
            "Enter both amounts for “Money in”.",
            problem(RuleDraft(conditions = listOf(RuleConditionDraft(RuleConditionField.MONEY_IN, "isbetween", amountText = "5")))),
        )
        assertEquals(
            "Enter an amount for “Money out”.",
            problem(RuleDraft(conditions = listOf(RuleConditionDraft(RuleConditionField.MONEY_OUT, "is", amountText = "abc")))),
        )
        assertEquals(
            "Enter a valid pattern for “Notes”.",
            problem(RuleDraft(conditions = listOf(RuleConditionDraft(RuleConditionField.NOTES, "matches", text = "(")))),
        )
        assertEquals(
            "Enter text for “Add to end of notes”.",
            problem(RuleDraft(conditions = filled, actions = listOf(RuleActionDraft(RuleActionKind.APPEND_NOTES)))),
        )
        assertEquals(
            "Each field can only be set once in a rule.",
            problem(
                RuleDraft(
                    conditions = filled,
                    actions =
                        listOf(
                            RuleActionDraft(RuleActionKind.SET_NOTES, text = "a"),
                            RuleActionDraft(RuleActionKind.SET_NOTES, text = "b"),
                        ),
                ),
            ),
        )
    }

    @Test
    fun changingFieldsKeepsValuesThatStillApply() {
        val amount = RuleConditionDraft(RuleConditionField.AMOUNT, "gt", amountText = "5")
        assertEquals(amount.copy(field = RuleConditionField.MONEY_OUT), amount.withField(RuleConditionField.MONEY_OUT))
        assertEquals(RuleConditionDraft(RuleConditionField.NOTES), amount.withField(RuleConditionField.NOTES))
        assertEquals(amount, amount.withField(RuleConditionField.AMOUNT))

        val payees = RuleConditionDraft(RuleConditionField.PAYEE, "oneOf", ids = listOf("p1", "p2"))
        assertEquals(listOf("p1"), payees.withOp("is").ids)
        assertEquals(payees, payees.withOp("oneOf"))
        assertTrue(payees.withOp("notOneOf").ids.size == 2)
    }
}
