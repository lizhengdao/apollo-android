// AUTO-GENERATED FILE. DO NOT MODIFY.
//
// This class was automatically generated by Apollo GraphQL plugin from the GraphQL queries it found.
// It should not be modified by hand.
//
package com.example.union_inline_fragments.adapter

import com.apollographql.apollo.api.ResponseField
import com.apollographql.apollo.api.internal.ResponseAdapter
import com.apollographql.apollo.api.internal.ResponseReader
import com.apollographql.apollo.api.internal.ResponseWriter
import com.example.union_inline_fragments.TestQuery
import com.example.union_inline_fragments.type.Episode
import kotlin.Array
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List

@Suppress("NAME_SHADOWING", "UNUSED_ANONYMOUS_PARAMETER", "LocalVariableName",
    "RemoveExplicitTypeArguments", "NestedLambdaShadowedImplicitParameter", "PropertyName",
    "RemoveRedundantQualifierName")
object TestQuery_ResponseAdapter : ResponseAdapter<TestQuery.Data> {
  private val RESPONSE_FIELDS: Array<ResponseField> = arrayOf(
    ResponseField.forList("search", "search", mapOf<String, Any>(
      "text" to "test"), true, null)
  )

  override fun fromResponse(reader: ResponseReader, __typename: String?): TestQuery.Data {
    return Data.fromResponse(reader, __typename)
  }

  override fun toResponse(writer: ResponseWriter, value: TestQuery.Data) {
    Data.toResponse(writer, value)
  }

  object Data : ResponseAdapter<TestQuery.Data> {
    private val RESPONSE_FIELDS: Array<ResponseField> = arrayOf(
      ResponseField.forList("search", "search", mapOf<String, Any>(
        "text" to "test"), true, null)
    )

    override fun fromResponse(reader: ResponseReader, __typename: String?): TestQuery.Data {
      return reader.run {
        var search: List<TestQuery.Data.Search?>? = null
        while(true) {
          when (selectField(RESPONSE_FIELDS)) {
            0 -> search = readList<TestQuery.Data.Search>(RESPONSE_FIELDS[0]) { reader ->
              reader.readObject<TestQuery.Data.Search> { reader ->
                Search.fromResponse(reader)
              }
            }
            else -> break
          }
        }
        TestQuery.Data(
          search = search
        )
      }
    }

    override fun toResponse(writer: ResponseWriter, value: TestQuery.Data) {
      writer.writeList(RESPONSE_FIELDS[0], value.search) { values, listItemWriter ->
        values?.forEach { value ->
          if(value == null) {
            listItemWriter.writeObject(null)
          } else {
            listItemWriter.writeObject { writer ->
              Search.toResponse(writer, value)
            }
          }
        }
      }
    }

    object Search : ResponseAdapter<TestQuery.Data.Search> {
      private val RESPONSE_FIELDS: Array<ResponseField> = arrayOf(
        ResponseField.forString("__typename", "__typename", null, false, null)
      )

      override fun fromResponse(reader: ResponseReader, __typename: String?):
          TestQuery.Data.Search {
        val typename = __typename ?: reader.readString(RESPONSE_FIELDS[0])
        return when(typename) {
          "Droid" -> CharacterSearch.fromResponse(reader, typename)
          "Human" -> CharacterSearch.fromResponse(reader, typename)
          "Starship" -> StarshipSearch.fromResponse(reader, typename)
          else -> OtherSearch.fromResponse(reader, typename)
        }
      }

      override fun toResponse(writer: ResponseWriter, value: TestQuery.Data.Search) {
        when(value) {
          is TestQuery.Data.Search.CharacterSearch -> CharacterSearch.toResponse(writer, value)
          is TestQuery.Data.Search.StarshipSearch -> StarshipSearch.toResponse(writer, value)
          is TestQuery.Data.Search.OtherSearch -> OtherSearch.toResponse(writer, value)
        }
      }

      object CharacterSearch : ResponseAdapter<TestQuery.Data.Search.CharacterSearch> {
        private val RESPONSE_FIELDS: Array<ResponseField> = arrayOf(
          ResponseField.forString("__typename", "__typename", null, false, null),
          ResponseField.forString("id", "id", null, false, null),
          ResponseField.forString("name", "name", null, false, null),
          ResponseField.forList("friends", "friends", null, true, null)
        )

        override fun fromResponse(reader: ResponseReader, __typename: String?):
            TestQuery.Data.Search.CharacterSearch {
          return reader.run {
            var __typename: String? = __typename
            var id: String? = null
            var name: String? = null
            var friends: List<TestQuery.Data.Search.CharacterSearch.Friend?>? = null
            while(true) {
              when (selectField(RESPONSE_FIELDS)) {
                0 -> __typename = readString(RESPONSE_FIELDS[0])
                1 -> id = readString(RESPONSE_FIELDS[1])
                2 -> name = readString(RESPONSE_FIELDS[2])
                3 -> friends = readList<TestQuery.Data.Search.CharacterSearch.Friend>(RESPONSE_FIELDS[3]) { reader ->
                  reader.readObject<TestQuery.Data.Search.CharacterSearch.Friend> { reader ->
                    Friend.fromResponse(reader)
                  }
                }
                else -> break
              }
            }
            TestQuery.Data.Search.CharacterSearch(
              __typename = __typename!!,
              id = id!!,
              name = name!!,
              friends = friends
            )
          }
        }

        override fun toResponse(writer: ResponseWriter,
            value: TestQuery.Data.Search.CharacterSearch) {
          writer.writeString(RESPONSE_FIELDS[0], value.__typename)
          writer.writeString(RESPONSE_FIELDS[1], value.id)
          writer.writeString(RESPONSE_FIELDS[2], value.name)
          writer.writeList(RESPONSE_FIELDS[3], value.friends) { values, listItemWriter ->
            values?.forEach { value ->
              if(value == null) {
                listItemWriter.writeObject(null)
              } else {
                listItemWriter.writeObject { writer ->
                  Friend.toResponse(writer, value)
                }
              }
            }
          }
        }

        object Friend : ResponseAdapter<TestQuery.Data.Search.CharacterSearch.Friend> {
          private val RESPONSE_FIELDS: Array<ResponseField> = arrayOf(
            ResponseField.forString("__typename", "__typename", null, false, null)
          )

          override fun fromResponse(reader: ResponseReader, __typename: String?):
              TestQuery.Data.Search.CharacterSearch.Friend {
            val typename = __typename ?: reader.readString(RESPONSE_FIELDS[0])
            return when(typename) {
              "Droid" -> CharacterDroidFriend.fromResponse(reader, typename)
              "Human" -> CharacterHumanFriend.fromResponse(reader, typename)
              else -> OtherFriend.fromResponse(reader, typename)
            }
          }

          override fun toResponse(writer: ResponseWriter,
              value: TestQuery.Data.Search.CharacterSearch.Friend) {
            when(value) {
              is TestQuery.Data.Search.CharacterSearch.Friend.CharacterDroidFriend -> CharacterDroidFriend.toResponse(writer, value)
              is TestQuery.Data.Search.CharacterSearch.Friend.CharacterHumanFriend -> CharacterHumanFriend.toResponse(writer, value)
              is TestQuery.Data.Search.CharacterSearch.Friend.OtherFriend -> OtherFriend.toResponse(writer, value)
            }
          }

          object CharacterDroidFriend :
              ResponseAdapter<TestQuery.Data.Search.CharacterSearch.Friend.CharacterDroidFriend> {
            private val RESPONSE_FIELDS: Array<ResponseField> = arrayOf(
              ResponseField.forString("__typename", "__typename", null, false, null),
              ResponseField.forString("name", "name", null, false, null),
              ResponseField.forString("primaryFunction", "primaryFunction", null, true, null),
              ResponseField.forList("friends", "friends", null, true, null)
            )

            override fun fromResponse(reader: ResponseReader, __typename: String?):
                TestQuery.Data.Search.CharacterSearch.Friend.CharacterDroidFriend {
              return reader.run {
                var __typename: String? = __typename
                var name: String? = null
                var primaryFunction: String? = null
                var friends: List<TestQuery.Data.Search.CharacterSearch.Friend.CharacterDroidFriend.Friend?>? = null
                while(true) {
                  when (selectField(RESPONSE_FIELDS)) {
                    0 -> __typename = readString(RESPONSE_FIELDS[0])
                    1 -> name = readString(RESPONSE_FIELDS[1])
                    2 -> primaryFunction = readString(RESPONSE_FIELDS[2])
                    3 -> friends = readList<TestQuery.Data.Search.CharacterSearch.Friend.CharacterDroidFriend.Friend>(RESPONSE_FIELDS[3]) { reader ->
                      reader.readObject<TestQuery.Data.Search.CharacterSearch.Friend.CharacterDroidFriend.Friend> { reader ->
                        Friend.fromResponse(reader)
                      }
                    }
                    else -> break
                  }
                }
                TestQuery.Data.Search.CharacterSearch.Friend.CharacterDroidFriend(
                  __typename = __typename!!,
                  name = name!!,
                  primaryFunction = primaryFunction,
                  friends = friends
                )
              }
            }

            override fun toResponse(writer: ResponseWriter,
                value: TestQuery.Data.Search.CharacterSearch.Friend.CharacterDroidFriend) {
              writer.writeString(RESPONSE_FIELDS[0], value.__typename)
              writer.writeString(RESPONSE_FIELDS[1], value.name)
              writer.writeString(RESPONSE_FIELDS[2], value.primaryFunction)
              writer.writeList(RESPONSE_FIELDS[3], value.friends) { values, listItemWriter ->
                values?.forEach { value ->
                  if(value == null) {
                    listItemWriter.writeObject(null)
                  } else {
                    listItemWriter.writeObject { writer ->
                      Friend.toResponse(writer, value)
                    }
                  }
                }
              }
            }

            object Friend :
                ResponseAdapter<TestQuery.Data.Search.CharacterSearch.Friend.CharacterDroidFriend.Friend>
                {
              private val RESPONSE_FIELDS: Array<ResponseField> = arrayOf(
                ResponseField.forString("id", "id", null, false, null)
              )

              override fun fromResponse(reader: ResponseReader, __typename: String?):
                  TestQuery.Data.Search.CharacterSearch.Friend.CharacterDroidFriend.Friend {
                return reader.run {
                  var id: String? = null
                  while(true) {
                    when (selectField(RESPONSE_FIELDS)) {
                      0 -> id = readString(RESPONSE_FIELDS[0])
                      else -> break
                    }
                  }
                  TestQuery.Data.Search.CharacterSearch.Friend.CharacterDroidFriend.Friend(
                    id = id!!
                  )
                }
              }

              override fun toResponse(writer: ResponseWriter,
                  value: TestQuery.Data.Search.CharacterSearch.Friend.CharacterDroidFriend.Friend) {
                writer.writeString(RESPONSE_FIELDS[0], value.id)
              }
            }
          }

          object CharacterHumanFriend :
              ResponseAdapter<TestQuery.Data.Search.CharacterSearch.Friend.CharacterHumanFriend> {
            private val RESPONSE_FIELDS: Array<ResponseField> = arrayOf(
              ResponseField.forString("__typename", "__typename", null, false, null),
              ResponseField.forString("name", "name", null, false, null),
              ResponseField.forString("homePlanet", "homePlanet", null, true, null),
              ResponseField.forList("friends", "friends", null, true, null)
            )

            override fun fromResponse(reader: ResponseReader, __typename: String?):
                TestQuery.Data.Search.CharacterSearch.Friend.CharacterHumanFriend {
              return reader.run {
                var __typename: String? = __typename
                var name: String? = null
                var homePlanet: String? = null
                var friends: List<TestQuery.Data.Search.CharacterSearch.Friend.CharacterHumanFriend.Friend?>? = null
                while(true) {
                  when (selectField(RESPONSE_FIELDS)) {
                    0 -> __typename = readString(RESPONSE_FIELDS[0])
                    1 -> name = readString(RESPONSE_FIELDS[1])
                    2 -> homePlanet = readString(RESPONSE_FIELDS[2])
                    3 -> friends = readList<TestQuery.Data.Search.CharacterSearch.Friend.CharacterHumanFriend.Friend>(RESPONSE_FIELDS[3]) { reader ->
                      reader.readObject<TestQuery.Data.Search.CharacterSearch.Friend.CharacterHumanFriend.Friend> { reader ->
                        Friend.fromResponse(reader)
                      }
                    }
                    else -> break
                  }
                }
                TestQuery.Data.Search.CharacterSearch.Friend.CharacterHumanFriend(
                  __typename = __typename!!,
                  name = name!!,
                  homePlanet = homePlanet,
                  friends = friends
                )
              }
            }

            override fun toResponse(writer: ResponseWriter,
                value: TestQuery.Data.Search.CharacterSearch.Friend.CharacterHumanFriend) {
              writer.writeString(RESPONSE_FIELDS[0], value.__typename)
              writer.writeString(RESPONSE_FIELDS[1], value.name)
              writer.writeString(RESPONSE_FIELDS[2], value.homePlanet)
              writer.writeList(RESPONSE_FIELDS[3], value.friends) { values, listItemWriter ->
                values?.forEach { value ->
                  if(value == null) {
                    listItemWriter.writeObject(null)
                  } else {
                    listItemWriter.writeObject { writer ->
                      Friend.toResponse(writer, value)
                    }
                  }
                }
              }
            }

            object Friend :
                ResponseAdapter<TestQuery.Data.Search.CharacterSearch.Friend.CharacterHumanFriend.Friend>
                {
              private val RESPONSE_FIELDS: Array<ResponseField> = arrayOf(
                ResponseField.forString("__typename", "__typename", null, false, null)
              )

              override fun fromResponse(reader: ResponseReader, __typename: String?):
                  TestQuery.Data.Search.CharacterSearch.Friend.CharacterHumanFriend.Friend {
                val typename = __typename ?: reader.readString(RESPONSE_FIELDS[0])
                return when(typename) {
                  "Droid" -> CharacterFriend.fromResponse(reader, typename)
                  "Human" -> CharacterFriend.fromResponse(reader, typename)
                  else -> OtherFriend.fromResponse(reader, typename)
                }
              }

              override fun toResponse(writer: ResponseWriter,
                  value: TestQuery.Data.Search.CharacterSearch.Friend.CharacterHumanFriend.Friend) {
                when(value) {
                  is TestQuery.Data.Search.CharacterSearch.Friend.CharacterHumanFriend.Friend.CharacterFriend -> CharacterFriend.toResponse(writer, value)
                  is TestQuery.Data.Search.CharacterSearch.Friend.CharacterHumanFriend.Friend.OtherFriend -> OtherFriend.toResponse(writer, value)
                }
              }

              object CharacterFriend :
                  ResponseAdapter<TestQuery.Data.Search.CharacterSearch.Friend.CharacterHumanFriend.Friend.CharacterFriend>
                  {
                private val RESPONSE_FIELDS: Array<ResponseField> = arrayOf(
                  ResponseField.forString("__typename", "__typename", null, false, null),
                  ResponseField.forEnum("firstAppearsIn", "firstAppearsIn", null, false, null)
                )

                override fun fromResponse(reader: ResponseReader, __typename: String?):
                    TestQuery.Data.Search.CharacterSearch.Friend.CharacterHumanFriend.Friend.CharacterFriend {
                  return reader.run {
                    var __typename: String? = __typename
                    var firstAppearsIn: Episode? = null
                    while(true) {
                      when (selectField(RESPONSE_FIELDS)) {
                        0 -> __typename = readString(RESPONSE_FIELDS[0])
                        1 -> firstAppearsIn = readString(RESPONSE_FIELDS[1])?.let { Episode.safeValueOf(it) }
                        else -> break
                      }
                    }
                    TestQuery.Data.Search.CharacterSearch.Friend.CharacterHumanFriend.Friend.CharacterFriend(
                      __typename = __typename!!,
                      firstAppearsIn = firstAppearsIn!!
                    )
                  }
                }

                override fun toResponse(writer: ResponseWriter,
                    value: TestQuery.Data.Search.CharacterSearch.Friend.CharacterHumanFriend.Friend.CharacterFriend) {
                  writer.writeString(RESPONSE_FIELDS[0], value.__typename)
                  writer.writeString(RESPONSE_FIELDS[1], value.firstAppearsIn.rawValue)
                }
              }

              object OtherFriend :
                  ResponseAdapter<TestQuery.Data.Search.CharacterSearch.Friend.CharacterHumanFriend.Friend.OtherFriend>
                  {
                private val RESPONSE_FIELDS: Array<ResponseField> = arrayOf(
                  ResponseField.forString("__typename", "__typename", null, false, null)
                )

                override fun fromResponse(reader: ResponseReader, __typename: String?):
                    TestQuery.Data.Search.CharacterSearch.Friend.CharacterHumanFriend.Friend.OtherFriend {
                  return reader.run {
                    var __typename: String? = __typename
                    while(true) {
                      when (selectField(RESPONSE_FIELDS)) {
                        0 -> __typename = readString(RESPONSE_FIELDS[0])
                        else -> break
                      }
                    }
                    TestQuery.Data.Search.CharacterSearch.Friend.CharacterHumanFriend.Friend.OtherFriend(
                      __typename = __typename!!
                    )
                  }
                }

                override fun toResponse(writer: ResponseWriter,
                    value: TestQuery.Data.Search.CharacterSearch.Friend.CharacterHumanFriend.Friend.OtherFriend) {
                  writer.writeString(RESPONSE_FIELDS[0], value.__typename)
                }
              }
            }
          }

          object OtherFriend :
              ResponseAdapter<TestQuery.Data.Search.CharacterSearch.Friend.OtherFriend> {
            private val RESPONSE_FIELDS: Array<ResponseField> = arrayOf(
              ResponseField.forString("__typename", "__typename", null, false, null)
            )

            override fun fromResponse(reader: ResponseReader, __typename: String?):
                TestQuery.Data.Search.CharacterSearch.Friend.OtherFriend {
              return reader.run {
                var __typename: String? = __typename
                while(true) {
                  when (selectField(RESPONSE_FIELDS)) {
                    0 -> __typename = readString(RESPONSE_FIELDS[0])
                    else -> break
                  }
                }
                TestQuery.Data.Search.CharacterSearch.Friend.OtherFriend(
                  __typename = __typename!!
                )
              }
            }

            override fun toResponse(writer: ResponseWriter,
                value: TestQuery.Data.Search.CharacterSearch.Friend.OtherFriend) {
              writer.writeString(RESPONSE_FIELDS[0], value.__typename)
            }
          }
        }
      }

      object StarshipSearch : ResponseAdapter<TestQuery.Data.Search.StarshipSearch> {
        private val RESPONSE_FIELDS: Array<ResponseField> = arrayOf(
          ResponseField.forString("__typename", "__typename", null, false, null),
          ResponseField.forString("name", "name", null, false, null)
        )

        override fun fromResponse(reader: ResponseReader, __typename: String?):
            TestQuery.Data.Search.StarshipSearch {
          return reader.run {
            var __typename: String? = __typename
            var name: String? = null
            while(true) {
              when (selectField(RESPONSE_FIELDS)) {
                0 -> __typename = readString(RESPONSE_FIELDS[0])
                1 -> name = readString(RESPONSE_FIELDS[1])
                else -> break
              }
            }
            TestQuery.Data.Search.StarshipSearch(
              __typename = __typename!!,
              name = name!!
            )
          }
        }

        override fun toResponse(writer: ResponseWriter,
            value: TestQuery.Data.Search.StarshipSearch) {
          writer.writeString(RESPONSE_FIELDS[0], value.__typename)
          writer.writeString(RESPONSE_FIELDS[1], value.name)
        }
      }

      object OtherSearch : ResponseAdapter<TestQuery.Data.Search.OtherSearch> {
        private val RESPONSE_FIELDS: Array<ResponseField> = arrayOf(
          ResponseField.forString("__typename", "__typename", null, false, null)
        )

        override fun fromResponse(reader: ResponseReader, __typename: String?):
            TestQuery.Data.Search.OtherSearch {
          return reader.run {
            var __typename: String? = __typename
            while(true) {
              when (selectField(RESPONSE_FIELDS)) {
                0 -> __typename = readString(RESPONSE_FIELDS[0])
                else -> break
              }
            }
            TestQuery.Data.Search.OtherSearch(
              __typename = __typename!!
            )
          }
        }

        override fun toResponse(writer: ResponseWriter, value: TestQuery.Data.Search.OtherSearch) {
          writer.writeString(RESPONSE_FIELDS[0], value.__typename)
        }
      }
    }
  }
}