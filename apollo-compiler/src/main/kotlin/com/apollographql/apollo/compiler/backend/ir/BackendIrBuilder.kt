package com.apollographql.apollo.compiler.backend.ir

import com.apollographql.apollo.compiler.PackageNameProvider
import com.apollographql.apollo.compiler.backend.ir.BackendIrMergeUtils.mergeFields
import com.apollographql.apollo.compiler.backend.ir.FrontendIrMergeUtils.mergeInlineFragmentsWithSameTypeConditions
import com.apollographql.apollo.compiler.backend.ir.SelectionKeyUtils.addFieldSelectionKey
import com.apollographql.apollo.compiler.backend.ir.SelectionKeyUtils.addFieldSelectionKeys
import com.apollographql.apollo.compiler.frontend.GQLBooleanValue
import com.apollographql.apollo.compiler.frontend.GQLDirective
import com.apollographql.apollo.compiler.frontend.GQLField
import com.apollographql.apollo.compiler.frontend.GQLFieldDefinition
import com.apollographql.apollo.compiler.frontend.GQLFragmentDefinition
import com.apollographql.apollo.compiler.frontend.GQLFragmentSpread
import com.apollographql.apollo.compiler.frontend.GQLInlineFragment
import com.apollographql.apollo.compiler.frontend.GQLNamedType
import com.apollographql.apollo.compiler.frontend.GQLOperationDefinition
import com.apollographql.apollo.compiler.frontend.GQLSelectionSet
import com.apollographql.apollo.compiler.frontend.GQLType
import com.apollographql.apollo.compiler.frontend.GQLValue
import com.apollographql.apollo.compiler.frontend.GQLVariableValue
import com.apollographql.apollo.compiler.frontend.Schema
import com.apollographql.apollo.compiler.frontend.SourceLocation
import com.apollographql.apollo.compiler.frontend.definitionFromScope
import com.apollographql.apollo.compiler.frontend.findDeprecationReason
import com.apollographql.apollo.compiler.frontend.leafType
import com.apollographql.apollo.compiler.frontend.possibleTypes
import com.apollographql.apollo.compiler.frontend.responseName
import com.apollographql.apollo.compiler.frontend.rootTypeDefinition
import com.apollographql.apollo.compiler.frontend.toKotlinValue
import com.apollographql.apollo.compiler.frontend.toSchemaType
import com.apollographql.apollo.compiler.frontend.toUtf8WithIndents
import com.apollographql.apollo.compiler.frontend.usedFragmentNames
import com.apollographql.apollo.compiler.frontend.validateAndCoerce
import com.apollographql.apollo.compiler.introspection.IntrospectionSchema

internal class BackendIrBuilder constructor(
    private val schema: Schema,
    private val fragmentDefinitions: Map<String, GQLFragmentDefinition>,
    private val useSemanticNaming: Boolean,
    val packageNameProvider: PackageNameProvider?
) {
  fun buildBackendIR(
      operations: List<GQLOperationDefinition>,
      fragments: List<GQLFragmentDefinition>
  ): BackendIr {
    return BackendIr(
        operations = operations.map { operation ->
          operation.buildBackendIrOperation()
        },
        fragments = fragments
            .map { fragment ->
              fragment.buildBackendIrNamedFragment()
            }
    )
  }

  /**
   * a GroupedField is a list of fields with the same responseName and arguments but possibly different selectionSets and directives
   */
  private data class GroupedField(val fields: List<GQLField>)

  private fun GQLValue.toBoolean(): Boolean? = when(this) {
    is GQLBooleanValue -> this.value
    else -> null
  }

  private fun GQLDirective.toBoolean(): Boolean? {
    return when (name) {
      "include" -> arguments!!.arguments.first().value.toBoolean()
      "skip" -> arguments!!.arguments.first().value.toBoolean()?.not()
      else -> null
    }
  }
  private fun GroupedField.removeLiteralDirectives(): GroupedField? {
    val newFields = fields.mapNotNull {
      val isAlwaysSkipped = it.directives.firstOrNull { it.toBoolean() == false } != null
      if (isAlwaysSkipped) {
        // 3.13.2 the field or fragment must not be queried if either the @skip condition is true or the @include condition is false.
        null
      } else {
        // Directives that are always true don't add any useful information
        it.copy(directives = it.directives.filter {
          it.toBoolean() != true
        })
      }
    }
    if (newFields.isEmpty()) {
      return null
    } else {
      return copy(fields = newFields)
    }
  }
  private fun GQLOperationDefinition.buildBackendIrOperation(): BackendIr.Operation {
    val normalizedName = this.normalizeOperationName()
    val rootTypeDefinition = this.rootTypeDefinition(schema)!!
    val selectionKey = SelectionKey(
        root = normalizedName,
        keys = listOf(normalizedName, "data"),
        type = SelectionKey.Type.Query,
    )
    val dataFieldDefinition = GQLFieldDefinition(
        description = "",
        name = "data",
        arguments = emptyList(),
        directives = emptyList(),
        type = GQLNamedType(name = rootTypeDefinition.name)
    )
    val dataField = GroupedField(
        fields = listOf(
            GQLField(
                name = "data",
                alias = null,
                sourceLocation = SourceLocation.UNKNOWN,
                arguments = null,
                directives = emptyList(),
                selectionSet = selectionSet
            )
        )
    ).buildBackendIrField(
        selectionKey = selectionKey,
        generateFragmentImplementations = true,
        fieldDefinition = dataFieldDefinition
    )
    val variables = this.variableDefinitions.map { variable ->
      BackendIr.Variable(
          name = variable.name,
          type = variable.type.toSchemaType(schema)
      )
    }
    val fragmentNames = usedFragmentNames(schema, fragmentDefinitions)
    return BackendIr.Operation(
        name = normalizedName,
        operationName = name!!,
        targetPackageName = packageNameProvider?.operationPackageName(filePath = sourceLocation.filePath ?: "") ?: "",
        operationType = IntrospectionSchema.TypeRef(
            IntrospectionSchema.Kind.OBJECT,
            rootTypeDefinition.name
        ),
        comment = this.description ?: "",
        variables = variables,
        definition = (toUtf8WithIndents() + "\n" + fragmentNames.joinToString(
            separator = "\n"
        ) { fragmentName ->
          fragmentDefinitions[fragmentName]!!.toUtf8WithIndents()
        }).trimEnd('\n'),
        dataField = dataField,
    )
  }

  private fun GQLOperationDefinition.normalizeOperationName(): String {
    fun normalizeOperationName(
        useSemanticNaming: Boolean,
        operationNameSuffix: String,
    ): String {
      require(name != null) {
        "anonymous operations are not supported. This should have been caught during validation"
      }
      return if (useSemanticNaming && !name.endsWith(operationNameSuffix)) {
        name.capitalize() + operationNameSuffix
      } else {
        name.capitalize()
      }
    }
    return normalizeOperationName(useSemanticNaming, operationType.capitalize())
  }

  private fun GroupedField.buildBackendIrField(
      selectionKey: SelectionKey,
      generateFragmentImplementations: Boolean,
      fieldDefinition: GQLFieldDefinition,
  ): BackendIr.Field {
    val first  = fields.first()
    val selections = fields.flatMap { it.selectionSet?.selections ?: emptyList() }

    val selectionSet = selections.filterIsInstance<GQLField>().buildBackendIrFields(
        selectionKey = selectionKey,
        generateFragmentImplementations = generateFragmentImplementations,
        parentType = fieldDefinition.type
    )
    val fragments = buildBackendIrFragments(
        parentSelectionName = first.responseName(),
        inlineFragments = selections.filterIsInstance<GQLInlineFragment>(),
        namedFragments = selections.filterIsInstance<GQLFragmentSpread>(),
        schemaType = fieldDefinition.type,
        selectionKey = selectionKey,
        selectionSet = selectionSet,
        generateFragmentImplementations = generateFragmentImplementations,
    )

    val arguments = first.arguments?.arguments?.map { argument ->
      val argumentType = fieldDefinition.arguments.first { it.name == argument.name }.type
      BackendIr.Argument(
          name = argument.name,
          value = argument.value.validateAndCoerce(argumentType, schema, null)
              .orThrow()
              .toKotlinValue(false),
          type = argumentType.toSchemaType(schema)
      )
    } ?: emptyList()

    val conditions = fields.map { it.directives.mapNotNull { it.toCondition() } }
        // TODO: this is wrong. If multiple field have @include/@skip directive, the resulting condition should be
        // evaluated or'ing all the conditions on individual fields
        .flatten()

    return BackendIr.Field(
        name = first.name,
        alias = first.alias,
        type = fieldDefinition.type.toSchemaType(schema),
        args = arguments,
        fields = selectionSet,
        fragments = fragments,
        deprecationReason = fieldDefinition.directives.findDeprecationReason(),
        description = fieldDefinition.description ?: "",
        conditions = conditions,
        selectionKeys = setOf(selectionKey),
    )
  }

  private fun GQLDirective.toCondition(): BackendIr.Condition? {
    if (arguments?.arguments?.size != 1) {
      // skip and include both have only one argument
      return null
    }

    val argument = arguments.arguments.first()

    check (argument.value is GQLVariableValue) {
      // see removeLiteralDirectives
      "@include/@skip directives with literal values should have been removed. This is a bug, please file it on github"
    }

    return when (name) {
      "skip",
      "include" -> BackendIr.Condition(
          kind = "BooleanCondition",
          variableName = argument.value.name,
          inverted = name == "skip",
          type = BackendIr.Condition.Type.Boolean
      )
      else -> null // unrecognized directive, skip
    }
  }

  private fun List<GQLField>.buildBackendIrFields(
      selectionKey: SelectionKey,
      generateFragmentImplementations: Boolean,
      parentType: GQLType
  ): List<BackendIr.Field> {
    return this.groupBy { it.responseName() }.map {
      GroupedField(it.value)
    }.mapNotNull { it.removeLiteralDirectives() }
        .map { groupedField ->
      val first = groupedField.fields.first()
      val fieldDefinition = first.definitionFromScope(schema, schema.typeDefinition(parentType.leafType().name))!!
      groupedField.buildBackendIrField(
          selectionKey = selectionKey + first.responseName(),
          generateFragmentImplementations = generateFragmentImplementations,
          fieldDefinition
      )
    }
  }

  private fun GQLFragmentDefinition.buildBackendIrNamedFragment(): BackendIr.NamedFragment {
    val selectionSet = buildSelectionSet(
        rootSelectionKey = SelectionKey(
            root = this.name,
            keys = listOf(this.name),
            type = SelectionKey.Type.Fragment,
        ),
        generateFragmentImplementations = false,
    )

    val defaultSelectionSet = buildSelectionSet(
        rootSelectionKey = SelectionKey(
            root = this.name,
            keys = listOf(this.name),
            type = SelectionKey.Type.Fragment,
        ),
        generateFragmentImplementations = true,
    )
        // as we generate default selection set for a fragment with `*Impl` suffix
        // we must patch all selection keys in this set by adding a new keys
        // these new keys are copies of existing ones with a different root
        // new root is `${this.fragmentName}Impl` instead of `${this.fragmentName}`
        // this is needed later when we generate models for default implementation of named fragments
        // to properly resolve inheritance to the original named fragment interfaces
        .patchWithDefaultImplementationSelectionKey(
            fragmentNameToPatch = this.name,
            defaultImplementationName = "${this.name}Impl",
        )
    return BackendIr.NamedFragment(
        name = this.name,
        defaultImplementationName = "${this.name}Impl",
        source = this.toUtf8WithIndents(),
        comment = this.description ?: "",
        selectionSet = selectionSet,
        defaultSelectionSet = defaultSelectionSet,
    )
  }

  private fun GQLFragmentDefinition.buildSelectionSet(
      rootSelectionKey: SelectionKey,
      generateFragmentImplementations: Boolean,
  ): BackendIr.NamedFragment.SelectionSet {
    val selectionSet = this.selectionSet.selections.filterIsInstance<GQLField>().buildBackendIrFields(
        selectionKey = rootSelectionKey,
        generateFragmentImplementations = generateFragmentImplementations,
        parentType = typeCondition
    )

    // resolve all possible types
    val possibleTypes = schema.typeDefinition(typeCondition.name).possibleTypes(schema.typeDefinitions)

    // build interfaces for the fragments
    val fragmentInterfaces = buildBackendIrFragmentInterfaces(
        inlineFragments = this.selectionSet.selections.filterIsInstance<GQLInlineFragment>(),
        namedFragments = this.selectionSet.selections.filterIsInstance<GQLFragmentSpread>(),
        fieldPossibleTypes = possibleTypes,
        selectionKey = rootSelectionKey,
        selectionSet = selectionSet,
    )

    // build implementations for the fragments if we allowed to do so
    val fragmentImplementations = if (generateFragmentImplementations) {
      buildFragmentImplementations(
          parentSelectionName = this.name,
          inlineFragments = this.selectionSet.selections.filterIsInstance<GQLInlineFragment>(),
          namedFragments = this.selectionSet.selections.filterIsInstance<GQLFragmentSpread>(),
          fieldPossibleTypes = possibleTypes,
          selectionKey = rootSelectionKey,
          selectionSet = selectionSet,
      )
    } else emptyList()

    return BackendIr.NamedFragment.SelectionSet(
        fields = selectionSet,
        fragments = fragmentInterfaces + fragmentImplementations,
        typeCondition = this.typeCondition.toSchemaType(schema),
        possibleTypes = possibleTypes.map { GQLNamedType(sourceLocation = SourceLocation.UNKNOWN, it).toSchemaType(schema) },
        selectionKeys = setOf(rootSelectionKey)
    )
  }

  // do deep recursive traversal of all fields and nested fragments
  // find any selection key that belongs to the named fragment we generate default selection set for (`root == fragmentNameToPatch`)
  // copy it with `defaultImplementationName` as a root and add to the original selection set
  private fun BackendIr.NamedFragment.SelectionSet.patchWithDefaultImplementationSelectionKey(
      fragmentNameToPatch: String,
      defaultImplementationName: String,
  ): BackendIr.NamedFragment.SelectionSet {
    return this.copy(
        fields = this.fields.map { field ->
          field.patchWithDefaultImplementationSelectionKey(
              fragmentNameToPatch = fragmentNameToPatch,
              defaultImplementationName = defaultImplementationName,
          )
        },
        fragments = fragments.map { fragment ->
          when (fragment) {
            is BackendIr.Fragment.Interface -> fragment.patchWithDefaultImplementationSelectionKey(
                fragmentNameToPatch = fragmentNameToPatch,
                defaultImplementationName = defaultImplementationName,
            )
            is BackendIr.Fragment.Implementation -> fragment.patchWithDefaultImplementationSelectionKey(
                fragmentToPatch = fragmentNameToPatch,
                fragmentDefaultImplementation = defaultImplementationName,
            )
          }
        },
        selectionKeys = this.selectionKeys + this.selectionKeys
            .filter { selectionKey ->
              selectionKey.type == SelectionKey.Type.Fragment && selectionKey.root == fragmentNameToPatch
            }
            .map { key ->
              key.copy(root = defaultImplementationName, keys = listOf(defaultImplementationName) + key.keys.drop(1))
            },
    )
  }

  // do deep recursive traversal of all fields and nested fragments
  // find any selection key that belongs to the named fragment we generate default selection set for (`root == fragmentNameToPatch`)
  // copy it with `defaultImplementationName` as a root and add to the original selection set
  private fun BackendIr.Field.patchWithDefaultImplementationSelectionKey(
      fragmentNameToPatch: String,
      defaultImplementationName: String,
  ): BackendIr.Field {
    return this.copy(
        selectionKeys = this.selectionKeys + this.selectionKeys
            .filter { selectionKey ->
              selectionKey.type == SelectionKey.Type.Fragment && selectionKey.root == fragmentNameToPatch
            }
            .map { key ->
              key.copy(root = defaultImplementationName, keys = listOf(defaultImplementationName) + key.keys.drop(1))
            },
        fields = this.fields.map { field ->
          field.patchWithDefaultImplementationSelectionKey(
              fragmentNameToPatch = fragmentNameToPatch,
              defaultImplementationName = defaultImplementationName,
          )
        },
        fragments = fragments.map { fragment ->
          when (fragment) {
            is BackendIr.Fragment.Interface -> fragment.patchWithDefaultImplementationSelectionKey(
                fragmentNameToPatch = fragmentNameToPatch,
                defaultImplementationName = defaultImplementationName,
            )
            is BackendIr.Fragment.Implementation -> fragment.patchWithDefaultImplementationSelectionKey(
                fragmentToPatch = fragmentNameToPatch,
                fragmentDefaultImplementation = defaultImplementationName,
            )
          }
        }
    )
  }

  // do deep recursive traversal of all fields and nested fragments
  // find any selection key that belongs to the named fragment we generate default selection set for (`root == fragmentNameToPatch`)
  // copy it with `defaultImplementationName` as a root and add to the original selection set
  private fun BackendIr.Fragment.Interface.patchWithDefaultImplementationSelectionKey(
      fragmentNameToPatch: String,
      defaultImplementationName: String,
  ): BackendIr.Fragment {
    return this.copy(
        selectionKeys = this.selectionKeys + this.selectionKeys
            .filter { selectionKey ->
              selectionKey.type == SelectionKey.Type.Fragment && selectionKey.root == fragmentNameToPatch
            }
            .map { key ->
              key.copy(root = defaultImplementationName, keys = listOf(defaultImplementationName) + key.keys.drop(1))
            },
        fields = this.fields.map { field ->
          field.patchWithDefaultImplementationSelectionKey(
              fragmentNameToPatch = fragmentNameToPatch,
              defaultImplementationName = defaultImplementationName,
          )
        },
    )
  }

  // do deep recursive traversal of all fields and nested fragments
  // find any selection key that belongs to the named fragment we generate default selection set for (`root == fragmentNameToPatch`)
  // copy it with `defaultImplementationName` as a root and add to the original selection set
  private fun BackendIr.Fragment.Implementation.patchWithDefaultImplementationSelectionKey(
      fragmentToPatch: String,
      fragmentDefaultImplementation: String,
  ): BackendIr.Fragment {
    return this.copy(
        selectionKeys = this.selectionKeys + this.selectionKeys
            .filter { selectionKey ->
              selectionKey.type == SelectionKey.Type.Fragment && selectionKey.root == fragmentToPatch
            }
            .map { key ->
              key.copy(root = fragmentDefaultImplementation, keys = listOf(fragmentDefaultImplementation) + key.keys.drop(1))
            },
        fields = this.fields.map { field ->
          field.patchWithDefaultImplementationSelectionKey(
              fragmentNameToPatch = fragmentToPatch,
              defaultImplementationName = fragmentDefaultImplementation,
          )
        },
    )
  }

  // builds fragment interfaces and implementations for given field
  private fun buildBackendIrFragments(
      parentSelectionName: String,
      inlineFragments: List<GQLInlineFragment>,
      namedFragments: List<GQLFragmentSpread>,
      schemaType: GQLType,
      selectionKey: SelectionKey,
      selectionSet: List<BackendIr.Field>,
      generateFragmentImplementations: Boolean,
  ): List<BackendIr.Fragment> {
    // resolve all field's possible types
    val possibleTypes = schema.typeDefinition(schemaType.leafType().name)
        .possibleTypes(schema.typeDefinitions)

    // build interfaces for the fragments
    val fragmentInterfaces = buildBackendIrFragmentInterfaces(
        inlineFragments = inlineFragments,
        namedFragments = namedFragments,
        fieldPossibleTypes = possibleTypes,
        selectionKey = selectionKey,
        selectionSet = selectionSet,
    )

    // build implementations for the fragments if we allowed to do so
    val fragmentImplementations = if (generateFragmentImplementations) {
      buildFragmentImplementations(
          parentSelectionName = parentSelectionName,
          inlineFragments = inlineFragments,
          namedFragments = namedFragments,
          fieldPossibleTypes = possibleTypes,
          selectionKey = selectionKey,
          selectionSet = selectionSet,
      )
    } else emptyList()

    return fragmentInterfaces + fragmentImplementations
  }

  private fun buildBackendIrFragmentInterfaces(
      inlineFragments: List<GQLInlineFragment>,
      namedFragments: List<GQLFragmentSpread>,
      fieldPossibleTypes: Set<String>,
      selectionKey: SelectionKey,
      selectionSet: List<BackendIr.Field>,
  ): List<BackendIr.Fragment.Interface> {
    // build all defined fragment interfaces including nested ones
    val fragments = buildGenericFragments(
        inlineFragments = inlineFragments,
        namedFragments = namedFragments,
        selectionKey = selectionKey,
        selectionSet = selectionSet,
        generateFragmentImplementations = false
    )
        // flatten fragments structure (remove nesting)
        .flatten()

    // we might get fragments defined with the same type condition - group them
    val groupedFragments = fragments.groupBy { fragment -> fragment.name }

    // merge fragments with the same type condition into one interface
    return groupedFragments.map { (_, fragments) ->
      val selectionSet = fragments.fold(emptyList<BackendIr.Field>()) { acc, fragment ->
        acc.mergeFields(fragment.selectionSet)
      }
      val selectionsKeys = fragments.fold(emptySet<SelectionKey>()) { acc, fragment ->
        acc.plus(fragment.selectionKeys)
      }
      // as fragment can be defined on interface that has more possible implementations than field type where it used
      // build intersection of fragment's and field's possible types
      val possibleTypes = fragments.first().possibleTypes.intersect(fieldPossibleTypes)
      BackendIr.Fragment.Interface(
          name = fragments.first().name,
          fields = selectionSet,
          selectionKeys = selectionsKeys,
          possibleTypes = possibleTypes.map { GQLNamedType(sourceLocation = SourceLocation.UNKNOWN, it).toSchemaType(schema) }.toSet(),
          description = fragments.first().description,
          typeCondition = fragments.first().typeCondition,
      )
    }
  }

  private fun buildFragmentImplementations(
      parentSelectionName: String,
      inlineFragments: List<GQLInlineFragment>,
      namedFragments: List<GQLFragmentSpread>,
      fieldPossibleTypes: Set<String>,
      selectionKey: SelectionKey,
      selectionSet: List<BackendIr.Field>,
  ): List<BackendIr.Fragment.Implementation> {
    // build all defined fragment implementations including nested ones
    val fragments = buildGenericFragments(
        inlineFragments = inlineFragments,
        namedFragments = namedFragments,
        selectionKey = selectionKey,
        selectionSet = selectionSet,
        generateFragmentImplementations = true
    )
        // flatten fragments structure (remove nesting)
        .flatten()

    // we might get fragments that intersects by possible type - group them
    val groupedFragments = fragments.groupFragmentsByPossibleTypes()

    // merge fragments with possible types intersection into one implementation
    return groupedFragments.map { (fragments, fragmentsPossibleTypes) ->
      val fragmentName = fragments.formatFragmentImplementationName(
          postfix = parentSelectionName,
      )
      val selectionSet = fragments.fold(emptyList<BackendIr.Field>()) { acc, fragment ->
        acc.mergeFields(fragment.selectionSet)
      }
      val selectionsKeys = fragments.fold(emptySet<SelectionKey>()) { acc, fragment ->
        acc.plus(fragment.selectionKeys)
      }
      val description = if (fragments.size == 1) {
        fragments.first().description
      } else null
      // as fragment can be defined on interface that has more possible implementation types than field's type where it used
      // build intersection of fragment's and field's possible types
      val possibleTypes = fragmentsPossibleTypes.intersect(fieldPossibleTypes)
      BackendIr.Fragment.Implementation(
          name = fragmentName,
          fields = selectionSet,
          possibleTypes = possibleTypes.map { GQLNamedType(sourceLocation = SourceLocation.UNKNOWN, it).toSchemaType(schema) }.toSet(),
          selectionKeys = selectionsKeys,
          description = description,
      )
    }
  }

  private fun List<GenericFragment>.flatten(): List<GenericFragment> {
    return this.flatMap { fragment ->
      listOf(fragment.copy(nestedFragments = emptyList())) + fragment.nestedFragments.flatten()
    }
  }

  private fun buildGenericFragments(
      inlineFragments: List<GQLInlineFragment>,
      namedFragments: List<GQLFragmentSpread>,
      selectionKey: SelectionKey,
      selectionSet: List<BackendIr.Field>,
      generateFragmentImplementations: Boolean,
  ): List<GenericFragment> {
    // build generic fragments from inline fragments
    val genericInlineFragments = inlineFragments
        .mergeInlineFragmentsWithSameTypeConditions()
        .map { inlineFragment ->
          inlineFragment.buildGenericFragment(
              parentSelectionKey = selectionKey,
              parentSelectionSet = selectionSet,
              parentNamedFragmentSelectionKeys = emptySet(),
              generateFragmentImplementations = generateFragmentImplementations
          )
        }
    // build generic fragments from named fragments
    val genericNamedFragments = namedFragments
        .map { fragmentSpread -> fragmentDefinitions.get(fragmentSpread.name)!! }
        .map { namedFragment ->
          namedFragment.buildGenericFragment(
              parentSelectionKey = selectionKey,
              parentSelectionSet = selectionSet,
              parentNamedFragmentSelectionKeys = emptySet(),
              generateFragmentImplementations = generateFragmentImplementations
          )
        }
    return genericInlineFragments + genericNamedFragments
  }

  private fun GQLInlineFragment.buildGenericFragment(
      parentSelectionKey: SelectionKey,
      parentSelectionSet: List<BackendIr.Field>,
      parentNamedFragmentSelectionKeys: Set<SelectionKey>,
      generateFragmentImplementations: Boolean,
  ): GenericFragment {
    val typeDefinition = schema.typeDefinition(typeCondition.name)
    return buildGenericFragment(
        fragmentTypeCondition = typeCondition.name,
        selectionSet = selectionSet,
        fragmentDescription = typeDefinition.description ?: "",
        fragmentConditions = directives.mapNotNull { it.toCondition() },
        namedFragmentSelectionKey = null,
        parentSelectionKey = parentSelectionKey,
        parentSelectionSet = parentSelectionSet,
        parentNamedFragmentSelectionKeys = parentNamedFragmentSelectionKeys,
        generateFragmentImplementations = generateFragmentImplementations
    )
  }

  private fun GQLFragmentDefinition.buildGenericFragment(
      parentSelectionKey: SelectionKey,
      parentSelectionSet: List<BackendIr.Field>,
      parentNamedFragmentSelectionKeys: Set<SelectionKey>,
      generateFragmentImplementations: Boolean,
  ): GenericFragment {
    return buildGenericFragment(
        fragmentTypeCondition = typeCondition.name,
        selectionSet = selectionSet,
        fragmentDescription = this.description ?: "",
        fragmentConditions = emptyList(),
        namedFragmentSelectionKey = SelectionKey(
            root = this.name.capitalize(),
            keys = listOf(this.name.capitalize()),
            type = SelectionKey.Type.Fragment,
        ),
        parentSelectionKey = parentSelectionKey,
        parentSelectionSet = parentSelectionSet,
        parentNamedFragmentSelectionKeys = parentNamedFragmentSelectionKeys,
        generateFragmentImplementations = generateFragmentImplementations
    )
  }

  /**
   * Build generic fragment with merged parent fields and any nested fragments.
   *
   * case 1:
   * ```
   * query TestQuery {
   *  hero {
   *    name
   *    ... on Human { <--- imagine we are building this fragment
   *      height
   *    }
   *  }
   *}
   * ```
   * we must carry down field `name` into built fragment for `Human`
   *
   * case 2:
   * ```
   * fragment HeroDetails on Character {
   *  id
   *  friends {
   *    name
   *  }
   *  ... on Droid { <--- imagine we are building this fragment
   *    name
   *    friends {
   *      id
   *    }
   *  }
   * ```
   * we must carry down `id` and `friends` fields (including any nested fields `friends.name`)
   * from the parent `HeroDetails` fragment
   *
   *
   * case 3:
   * ```
   * fragment HeroDetails on Character {
   *  name
   *  ... on Droid { <--- imagine we are building this fragment
   *    id
   *    ...DroidDetails
   *  }
   *}
   *
   *fragment DroidDetails on Droid {
   *  friends {
   *    name
   *  }
   * }
   * ```
   */
  private fun buildGenericFragment(
      fragmentTypeCondition: String,
      selectionSet: GQLSelectionSet,
      fragmentDescription: String,
      fragmentConditions: List<BackendIr.Condition>,
      namedFragmentSelectionKey: SelectionKey?,
      parentSelectionKey: SelectionKey,
      parentSelectionSet: List<BackendIr.Field>,
      parentNamedFragmentSelectionKeys: Set<SelectionKey>,
      generateFragmentImplementations: Boolean,
  ): GenericFragment {
    val fragmentName = fragmentTypeCondition.capitalize()
    val fragmentSelectionSet = selectionSet.selections.filterIsInstance<GQLField>().buildBackendIrFields(
        selectionKey = parentSelectionKey + fragmentName,
        generateFragmentImplementations = generateFragmentImplementations,
        parentType = GQLNamedType(sourceLocation = SourceLocation.UNKNOWN, fragmentTypeCondition)
    ).addFieldSelectionKey(namedFragmentSelectionKey)
    val parentSelectionSet = parentSelectionSet
        .addFieldSelectionKey(parentSelectionKey + fragmentName)
        .map { field ->
          field.addFieldSelectionKeys(
              field.selectionKeys
                  .filter { selectionKey -> selectionKey.type == SelectionKey.Type.Fragment }
                  .map { selectionKey ->
                    selectionKey.copy(
                        keys = listOf(selectionKey.keys.first(), fragmentName, selectionKey.keys.last())
                    )
                  }
                  .toSet()
          )
        }
        .mergeFields(fragmentSelectionSet)
    val childInlineFragments = selectionSet.selections.filterIsInstance<GQLInlineFragment>().map { inlineFragment ->
      val fragment = buildGenericFragment(
          fragmentTypeCondition = inlineFragment.typeCondition.name,
          selectionSet = inlineFragment.selectionSet,
          fragmentDescription = schema.typeDefinition(inlineFragment.typeCondition.name).description ?: "",
          fragmentConditions = inlineFragment.directives.mapNotNull { it.toCondition() },
          namedFragmentSelectionKey = namedFragmentSelectionKey?.let { selectionKey ->
            selectionKey.copy(
                keys = listOf(selectionKey.keys.first(), inlineFragment.typeCondition.name.capitalize())
            )
          },
          parentSelectionKey = parentSelectionKey,
          parentSelectionSet = parentSelectionSet,
          parentNamedFragmentSelectionKeys = parentNamedFragmentSelectionKeys.run {
            if (namedFragmentSelectionKey != null) {
              this + namedFragmentSelectionKey
            } else this
          },
          generateFragmentImplementations = generateFragmentImplementations
      )
      fragment.copy(
          selectionKeys = fragment.selectionKeys + (parentSelectionKey + fragmentName)
      )
    }
    val childNamedFragments = selectionSet.selections.filterIsInstance<GQLFragmentSpread>().map { namedFragment ->
      val fragmentDefinition = fragmentDefinitions.get(namedFragment.name)!!
      buildGenericFragment(
          fragmentTypeCondition = fragmentDefinition.typeCondition.name,
          selectionSet = fragmentDefinition.selectionSet,
          fragmentDescription = fragmentDefinition.description ?: "",
          fragmentConditions = emptyList(),
          namedFragmentSelectionKey = SelectionKey(
              root = namedFragment.name.capitalize(),
              keys = listOf(namedFragment.name.capitalize()),
              type = SelectionKey.Type.Fragment,
          ),
          parentSelectionKey = parentSelectionKey,
          parentSelectionSet = parentSelectionSet,
          parentNamedFragmentSelectionKeys = parentNamedFragmentSelectionKeys.run {
            if (namedFragmentSelectionKey != null) {
              this + namedFragmentSelectionKey
            } else this
          },
          generateFragmentImplementations = generateFragmentImplementations
      )
    }
    val selectionKeys = setOf(
        parentSelectionKey,
        parentSelectionKey + fragmentName,
    ).plus(
        parentNamedFragmentSelectionKeys.map { selectionKey ->
          //TODO figure out why this happens when we have nested fragments defined on the same type condition
          selectionKey
              .takeIf { selectionKey.keys.last() == fragmentName }
              ?: selectionKey + fragmentName
        }
    ).run {
      if (namedFragmentSelectionKey != null) {
        this + namedFragmentSelectionKey
      } else this
    }
    return GenericFragment(
        name = fragmentName,
        typeCondition = GQLNamedType(sourceLocation = SourceLocation.UNKNOWN, fragmentTypeCondition).toSchemaType(schema),
        possibleTypes = schema.typeDefinition(fragmentTypeCondition).possibleTypes(schema.typeDefinitions).toList(),
        description = fragmentDescription,
        selectionSet = parentSelectionSet,
        conditions = fragmentConditions,
        nestedFragments = childInlineFragments + childNamedFragments,
        selectionKeys = selectionKeys,
    )
  }

  /**
   * Formats fragment implementation name by joining type conditions:
   * ```
   *  query TestOperation {
   *   random {
   *    ... on Being {
   *    }
   *    ... on Human {
   *    }
   *  }
   *}
   * ```
   * generated name is going to be `BeingHumanRandom`.
   */
  private fun List<GenericFragment>.formatFragmentImplementationName(postfix: String): String {
    return this
        .distinctBy { fragment -> fragment.typeCondition }
        .joinToString(separator = "", postfix = postfix.capitalize()) { fragment ->
          fragment.typeCondition.name!!.capitalize()
        }
  }

  /**
   * Groups provided list of fragments by intersection of possible types.
   * ```
   * query TestOperation {
   *   random {
   *     ... on Being {
   *      ... on Human {
   *      }
   *      ... on Wookie {
   *      }
   *     }
   *  }
   *}
   * ```
   * as `Human` and `Wookie` are subtypes of `Being` grouped fragment map is going to be:
   * ```
   * [
   *  [Being, Human]: ["Human"],
   *  [Being, Wookie]: ["Wookie"]
   * ]
   * ```
   */
  private fun List<GenericFragment>.groupFragmentsByPossibleTypes()
      : Map<List<GenericFragment>, List<String>> {
    return this
        .flatMap { fragment -> fragment.possibleTypes }
        .toSet()
        .map { possibleType ->
          possibleType to this.filter { fragment ->
            fragment.possibleTypes.contains(possibleType)
          }
        }.fold(emptyMap()) { acc, (possibleType, fragments) ->
          acc + (fragments to (acc[fragments]?.plus(possibleType) ?: listOf(possibleType)))
        }
  }

  private data class GenericFragment(
      val name: String,
      val typeCondition: IntrospectionSchema.TypeRef,
      val possibleTypes: List<String>,
      val description: String,
      val selectionSet: List<BackendIr.Field>,
      val conditions: List<BackendIr.Condition>,
      val nestedFragments: List<GenericFragment>,
      val selectionKeys: Set<SelectionKey>,
  )
}
