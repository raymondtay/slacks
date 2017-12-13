package slacks.core.program

import providers.slack.algebra._
import providers.slack.models.SlackAccessToken
import slacks.core.config.SlackAccessConfig

import akka.actor._
import akka.http.scaladsl.{Http, HttpExt}
import akka.http.scaladsl.model._
import akka.stream.{ ActorMaterializer, ActorMaterializerSettings }
import akka.util.{ByteString, Timeout}

// Service stubs for testing purposes
//

class FakeChannelListingHttpService extends HttpService {
  import cats.data.Kleisli, cats.implicits._
  import ContentTypes._
  import scala.concurrent.Future
  override def makeSingleRequest(implicit http: HttpExt, akkaMat: ActorMaterializer) = Kleisli{ 
    (_uri: String) ⇒ 
        val jsonData = """
        {"ok":true,"channels":[{"id":"C024Z5MQT","name":"general","is_channel":true,"created":1391648421,"creator":"U024Z5MQP","is_archived":false,"is_general":true,"unlinked":0,"name_normalized":"general","is_shared":false,"is_org_shared":false,"is_member":true,"is_private":false,"is_mpim":false,"members":["U024Z5MQP","U024ZCABY","U024ZCR04","U024ZH7HL","U0250SQLD","U02518S6S","U029A9L6M","U029ACXNZ","U02EJ9QKJ","U02MR8EG8","U02PY6S73","U030MHXHX","U034URXDR","U03C98L5C","U03CKFGU5","U047EAUB4","U0483ASQP","U049K6V1G","U04MGHVRY","U0790EWUW","U086LTM6W","U08GD90CC","U08TDQVNG","U0AM39YTX","U0CDW37RA","U0CE9A2E5","U0DATFFH9","U0F3F6F38","U0FB8THB8","U0FJKS5MM","U0G1H4L3E","U0GAZLRPW","U0L251X5W","U0LPSJQR0","U0PL0HUHG","U0RBSN9D1","U0X3L1PS7","U10H6PUSJ","U17RGMDU4","U193XDML7","U1NG7CPBK","U1NGC3ZPT","U1SF636UB","U23D7H5MZ","U2748C06S","U2FQG2G9F","U2M8UH9SM","U2Q2U37SA","U2YAZS40Y","U2Z0ARK2P","U31B3PV17","U37BF9457","U39R1AT9D","U3ACT6Z2P","U3LRTQ8G1","U3NND6PV1","U3RUCKH5J","U41CSF56Z","U43LNT57T","U43Q2RJ8H","U497EFER0","U4AFYEWBG","U4B93DBDX","U4BUQR94L","U4U2WKX7X","U4W385673","U543VFD3Q","U56JZMQ0Y","U575BN3H9","U577BHBNW","U58LY38Q6","U5K7JUATE","U5TEUA60Z","U5UG5NU6T","U5ZV5797E","U642GGK9R","U664CEM4L","U66T9CNBG","U6QFZ585N","U6R7SU9P0","U74K31TA9","U7JKEFHM0","U7SG2QG2D","U7V7V7NFM","U81GPG5HV"],"topic":{"value":"the day @thu was dethroned https:\/\/nugit.slack.com\/archives\/general\/p1476945193000075","creator":"U04MGHVRY","last_set":1499417480},"purpose":{"value":"The #general channel is for team-wide communication and announcements. All team members are in this channel.","creator":"","last_set":0},"previous_names":[],"num_members":38},{"id":"C024Z65M7","name":"dev-log","is_channel":true,"created":1391651270,"creator":"U024Z5MQP","is_archived":true,"is_general":false,"unlinked":0,"name_normalized":"dev-log","is_shared":false,"is_org_shared":false,"is_member":false,"is_private":false,"is_mpim":false,"members":[],"topic":{"value":"Updates on Github commits across all Nugit repositories.","creator":"U024Z5MQP","last_set":1400065716},"purpose":{"value":"Updates on Github commits across all Nugit repositories","creator":"U024Z5MQP","last_set":1400065746},"previous_names":["dev"],"num_members":0}],"response_metadata":{"next_cursor":"dGVhbTpDMDI0WkNWOFg="}}
        """
 
      Future.successful(
        HttpResponse(entity = HttpEntity(`application/json`, jsonData))
      )
  }
}

class FakeChannelHistoryHttpService extends HttpService {
  import cats.data.Kleisli, cats.implicits._
  import ContentTypes._
  import scala.concurrent.Future
  override def makeSingleRequest(implicit http: HttpExt, akkaMat: ActorMaterializer) = Kleisli{ 
    (_uri: String) ⇒ 
        val jsonData = """
        {"ok":true,"messages":[{"text":"Hello. Another new user going by the name of Tracey Rountree (<mailto:tracey.rountree@netbooster.com|tracey.rountree@netbooster.com>) has passed through the Nugit gates. We've also added them to Mailchimp's Nugit Users list.  ","username":"Zapier","bot_id":"B0VD275DX","type":"message","subtype":"bot_message","ts":"1511157663.000229","reactions":[{"name":"tada","users":["U81GPG5HV"],"count":1}]},{"text":"","bot_id":"B139D7CUV","attachments":[{"fallback":"Varun received a :bulb: 5 bonus from Paul: +5 @varun for completing all sprint tasks two weeks ago #speedofbusiness #trailblazer","text":"*+5* @varun for completing all sprint tasks two weeks ago #speedofbusiness #trailblazer\n<https:\/\/bonus.ly\/bonuses\/5a126c9475d0770b6382dbf8?utm_source=bonus.ly&amp;utm_medium=chat&amp;utm_campaign=slack#add-on|Add on to this bonus?>","pretext":"<https:\/\/bonus.ly\/bonuses\/5a126c9475d0770b6382dbf8?utm_source=bonus.ly&amp;utm_medium=chat&amp;utm_campaign=slack|Varun received a bonus from Paul>","id":1,"color":"33CC66","mrkdwn_in":["text"]}],"type":"message","subtype":"bot_message","ts":"1511156887.000150"}],"has_more":false,"pin_count":27,"response_metadata":{"next_cursor":""}}"""
 
      Future.successful(
        HttpResponse(entity = HttpEntity(`application/json`, jsonData))
      )
  }
}

class FakeChannelConversationHistoryHttpService extends HttpService {
  import cats.data.Kleisli, cats.implicits._
  import ContentTypes._
  import scala.concurrent.Future
  override def makeSingleRequest(implicit http: HttpExt, akkaMat: ActorMaterializer) = Kleisli{ 
    (_uri: String) ⇒ 
        val jsonData = """
        {
   "has_more" : true,
   "messages" : [
      {
         "type" : "message",
         "ts" : "1513078965.000241",
         "user" : "U0F3F6F38",
         "text" : "<@U04MGHVRY> <https://blogs.microsoft.com/on-the-issues/?p=56086>",
         "attachments" : [
            {
               "ts" : 1512990007,
               "title_link" : "https://blogs.microsoft.com/on-the-issues/?p=56086",
               "title" : "AI for Earth can be a game-changer for our planet - Microsoft on the Issues",
               "service_name" : "Microsoft on the Issues",
               "text" : "On the two-year anniversary of the Paris climate accord, the world’s government, civic and business leaders are coming together in Paris to discuss one of the most important issues and opportunities of our time, climate change. I’m excited to lead the Microsoft delegation at these meetings. While the experts’ warnings are dire, at Microsoft we...",
               "thumb_height" : 576,
               "service_icon" : "https://mscorpmedia.azureedge.net/mscorpmedia/2017/08/favicon-599dd744b8cac.jpg",
               "thumb_width" : 1024,
               "thumb_url" : "https://mscorpmedia.azureedge.net/mscorpmedia/2017/12/AI4Earth-1024x576.jpg",
               "fallback" : "Microsoft on the Issues: AI for Earth can be a game-changer for our planet - Microsoft on the Issues",
               "id" : 1,
               "from_url" : "https://blogs.microsoft.com/on-the-issues/?p=56086"
            }
         ]
      },
        {
           "type" : "message",
           "bot_id" : "B0VD275DX",
           "text" : "Hello. Another new user going by the name of Nancy Bernard (<mailto:nancy.bernard@mindshareworld.com|nancy.bernard@mindshareworld.com>) has passed through the Nugit gates. We've also added them to Mailchimp's Nugit Users list.  ",
           "username" : "Zapier",
           "ts" : "1513073709.000326",
           "subtype" : "bot_message"
        },
        {
           "type" : "message",
           "bot_id" : "B3DGC7129",
           "attachments" : [
              {
                 "image_url" : "https://media3.giphy.com/media/R54jhpzpARmVy/giphy-downsized.gif",
                 "footer" : "Posted using /giphy",
                 "image_height" : 194,
                 "image_width" : 320,
                 "fallback" : "giphy: https://giphy.com/gifs/shake-fist-angry-girl-shakes-R54jhpzpARmVy",
                 "image_bytes" : 759548,
                 "id" : 1,
                 "title" : "shakes fist",
                 "title_link" : "https://giphy.com/gifs/shake-fist-angry-girl-shakes-R54jhpzpARmVy",
                 "is_animated" : true
              }
           ],
           "text" : "",
           "user" : "U5TEUA60Z",
           "ts" : "1512625849.000036",
           "reactions" : [
              {
                 "name" : "joy",
                 "count" : 1,
                 "users" : [
                    "U7V7V7NFM"
                 ]
              }
           ]
        },
      {
         "comment" : {
            "timestamp" : 1512717689,
            "created" : 1512717689,
            "comment" : "you mean `waifu`?",
            "user" : "U0PL0HUHG",
            "is_intro" : false,
            "id" : "Fc8BKSAGPL"
         },
         "ts" : "1512717689.000010",
         "file" : {
            "thumb_360_h" : 166,
            "comments_count" : 5,
            "mimetype" : "image/jpeg",
            "permalink" : "https://nugit.slack.com/files/U024ZH7HL/F8CMV2GTH/image_uploaded_from_ios.jpg",
            "thumb_480" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_480.jpg",
            "is_public" : true,
            "thumb_480_h" : 222,
            "original_h" : 637,
            "thumb_960_h" : 443,
            "filetype" : "jpg",
            "original_w" : 1380,
            "thumb_360_w" : 360,
            "editable" : false,
            "thumb_160" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_160.jpg",
            "permalink_public" : "https://slack-files.com/T024Z5MQM-F8CMV2GTH-e2fa366d26",
            "thumb_720_w" : 720,
            "title" : "Left is Singapore, right is China. The survey question is, \"I am comfortable with the idea of artificial intelligence/machines acting as...\"",
            "thumb_800_w" : 800,
            "size" : 429223,
            "thumb_800_h" : 369,
            "timestamp" : 1512717302,
            "external_type" : "",
            "url_private_download" : "https://files.slack.com/files-pri/T024Z5MQM-F8CMV2GTH/download/image_uploaded_from_ios.jpg",
            "is_external" : false,
            "id" : "F8CMV2GTH",
            "name" : "Image uploaded from iOS.jpg",
            "thumb_960_w" : 960,
            "thumb_360" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_360.jpg",
            "thumb_720_h" : 332,
            "public_url_shared" : false,
            "username" : "",
            "thumb_64" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_64.jpg",
            "url_private" : "https://files.slack.com/files-pri/T024Z5MQM-F8CMV2GTH/image_uploaded_from_ios.jpg",
            "ims" : [],
            "user" : "U024ZH7HL",
            "mode" : "hosted",
            "thumb_1024_w" : 1024,
            "groups" : [],
            "created" : 1512717302,
            "image_exif_rotation" : 1,
            "display_as_bot" : false,
            "thumb_480_w" : 480,
            "thumb_800" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_800.jpg",
            "thumb_960" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_960.jpg",
            "thumb_1024_h" : 473,
            "channels" : [
               "C024Z5MQT"
            ],
            "pretty_type" : "JPEG",
            "thumb_80" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_80.jpg",
            "thumb_1024" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_1024.jpg",
            "thumb_720" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_720.jpg"
         },
         "text" : "<@U0PL0HUHG> commented on <@U024ZH7HL>’s file <https://nugit.slack.com/files/U024ZH7HL/F8CMV2GTH/image_uploaded_from_ios.jpg|Left is Singapore, right is China. The survey question is, \"I am comfortable with the idea of artificial intelligence/machines acting as...\">: you mean `waifu`?",
         "type" : "message",
         "subtype" : "file_comment",
         "is_intro" : false
      },
      {
         "is_intro" : false,
         "subtype" : "file_comment",
         "type" : "message",
         "text" : "<@U4BUQR94L> commented on <@U024ZH7HL>’s file <https://nugit.slack.com/files/U024ZH7HL/F8CMV2GTH/image_uploaded_from_ios.jpg|Left is Singapore, right is China. The survey question is, \"I am comfortable with the idea of artificial intelligence/machines acting as...\">: .",
         "file" : {
            "thumb_360" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_360.jpg",
            "thumb_720_h" : 332,
            "thumb_960_w" : 960,
            "public_url_shared" : false,
            "username" : "",
            "url_private" : "https://files.slack.com/files-pri/T024Z5MQM-F8CMV2GTH/image_uploaded_from_ios.jpg",
            "thumb_64" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_64.jpg",
            "user" : "U024ZH7HL",
            "mode" : "hosted",
            "ims" : [],
            "groups" : [],
            "thumb_1024_w" : 1024,
            "created" : 1512717302,
            "image_exif_rotation" : 1,
            "display_as_bot" : false,
            "thumb_480_w" : 480,
            "thumb_960" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_960.jpg",
            "thumb_800" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_800.jpg",
            "thumb_1024_h" : 473,
            "channels" : [
               "C024Z5MQT"
            ],
            "thumb_80" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_80.jpg",
            "pretty_type" : "JPEG",
            "thumb_720" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_720.jpg",
            "thumb_1024" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_1024.jpg",
            "comments_count" : 5,
            "mimetype" : "image/jpeg",
            "permalink" : "https://nugit.slack.com/files/U024ZH7HL/F8CMV2GTH/image_uploaded_from_ios.jpg",
            "thumb_360_h" : 166,
            "thumb_480" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_480.jpg",
            "thumb_480_h" : 222,
            "is_public" : true,
            "original_h" : 637,
            "filetype" : "jpg",
            "thumb_960_h" : 443,
            "editable" : false,
            "original_w" : 1380,
            "thumb_360_w" : 360,
            "permalink_public" : "https://slack-files.com/T024Z5MQM-F8CMV2GTH-e2fa366d26",
            "thumb_160" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_160.jpg",
            "thumb_800_w" : 800,
            "title" : "Left is Singapore, right is China. The survey question is, \"I am comfortable with the idea of artificial intelligence/machines acting as...\"",
            "thumb_720_w" : 720,
            "thumb_800_h" : 369,
            "timestamp" : 1512717302,
            "size" : 429223,
            "external_type" : "",
            "id" : "F8CMV2GTH",
            "url_private_download" : "https://files.slack.com/files-pri/T024Z5MQM-F8CMV2GTH/download/image_uploaded_from_ios.jpg",
            "is_external" : false,
            "name" : "Image uploaded from iOS.jpg"
         },
         "ts" : "1512717607.000060",
         "comment" : {
            "comment" : ".",
            "reactions" : [
               {
                  "users" : [
                     "U5ZV5797E"
                  ],
                  "name" : "robot_face",
                  "count" : 1
               }
            ],
            "created" : 1512717607,
            "timestamp" : 1512717607,
            "is_intro" : false,
            "id" : "Fc8BM09PS8",
            "user" : "U4BUQR94L"
         }
      },
      {
         "comment" : {
            "user" : "U3ACT6Z2P",
            "is_intro" : false,
            "id" : "Fc8BHL2F0T",
            "timestamp" : 1512717491,
            "comment" : "oh yes, I thought it was cut off :neutral_face:",
            "created" : 1512717491
         },
         "ts" : "1512717491.000024",
         "text" : "<@U3ACT6Z2P> commented on <@U024ZH7HL>’s file <https://nugit.slack.com/files/U024ZH7HL/F8CMV2GTH/image_uploaded_from_ios.jpg|Left is Singapore, right is China. The survey question is, \"I am comfortable with the idea of artificial intelligence/machines acting as...\">: oh yes, I thought it was cut off :neutral_face:",
         "file" : {
            "name" : "Image uploaded from iOS.jpg",
            "external_type" : "",
            "id" : "F8CMV2GTH",
            "is_external" : false,
            "url_private_download" : "https://files.slack.com/files-pri/T024Z5MQM-F8CMV2GTH/download/image_uploaded_from_ios.jpg",
            "thumb_800_h" : 369,
            "timestamp" : 1512717302,
            "size" : 429223,
            "thumb_800_w" : 800,
            "title" : "Left is Singapore, right is China. The survey question is, \"I am comfortable with the idea of artificial intelligence/machines acting as...\"",
            "thumb_720_w" : 720,
            "editable" : false,
            "thumb_360_w" : 360,
            "original_w" : 1380,
            "permalink_public" : "https://slack-files.com/T024Z5MQM-F8CMV2GTH-e2fa366d26",
            "thumb_160" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_160.jpg",
            "filetype" : "jpg",
            "thumb_960_h" : 443,
            "is_public" : true,
            "thumb_480_h" : 222,
            "original_h" : 637,
            "comments_count" : 5,
            "mimetype" : "image/jpeg",
            "permalink" : "https://nugit.slack.com/files/U024ZH7HL/F8CMV2GTH/image_uploaded_from_ios.jpg",
            "thumb_360_h" : 166,
            "thumb_480" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_480.jpg",
            "thumb_720" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_720.jpg",
            "thumb_1024" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_1024.jpg",
            "thumb_1024_h" : 473,
            "channels" : [
               "C024Z5MQT"
            ],
            "thumb_80" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_80.jpg",
            "pretty_type" : "JPEG",
            "thumb_960" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_960.jpg",
            "thumb_800" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_800.jpg",
            "display_as_bot" : false,
            "image_exif_rotation" : 1,
            "thumb_480_w" : 480,
            "groups" : [],
            "thumb_1024_w" : 1024,
            "created" : 1512717302,
            "url_private" : "https://files.slack.com/files-pri/T024Z5MQM-F8CMV2GTH/image_uploaded_from_ios.jpg",
            "thumb_64" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_64.jpg",
            "mode" : "hosted",
            "user" : "U024ZH7HL",
            "ims" : [],
            "username" : "",
            "thumb_360" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_360.jpg",
            "thumb_720_h" : 332,
            "thumb_960_w" : 960,
            "public_url_shared" : false
         },
         "type" : "message",
         "subtype" : "file_comment",
         "is_intro" : false
      },
      {
         "file" : {
            "thumb_800" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_800.jpg",
            "thumb_960" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_960.jpg",
            "image_exif_rotation" : 1,
            "display_as_bot" : false,
            "thumb_480_w" : 480,
            "thumb_1024" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_1024.jpg",
            "thumb_720" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_720.jpg",
            "channels" : [
               "C024Z5MQT"
            ],
            "thumb_1024_h" : 473,
            "pretty_type" : "JPEG",
            "thumb_80" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_80.jpg",
            "username" : "",
            "thumb_960_w" : 960,
            "thumb_360" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_360.jpg",
            "thumb_720_h" : 332,
            "public_url_shared" : false,
            "thumb_1024_w" : 1024,
            "groups" : [],
            "created" : 1512717302,
            "thumb_64" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_64.jpg",
            "url_private" : "https://files.slack.com/files-pri/T024Z5MQM-F8CMV2GTH/image_uploaded_from_ios.jpg",
            "ims" : [],
            "mode" : "hosted",
            "user" : "U024ZH7HL",
            "size" : 429223,
            "timestamp" : 1512717302,
            "thumb_800_h" : 369,
            "thumb_720_w" : 720,
            "title" : "Left is Singapore, right is China. The survey question is, \"I am comfortable with the idea of artificial intelligence/machines acting as...\"",
            "thumb_800_w" : 800,
            "name" : "Image uploaded from iOS.jpg",
            "external_type" : "",
            "is_external" : false,
            "url_private_download" : "https://files.slack.com/files-pri/T024Z5MQM-F8CMV2GTH/download/image_uploaded_from_ios.jpg",
            "id" : "F8CMV2GTH",
            "thumb_480_h" : 222,
            "is_public" : true,
            "original_h" : 637,
            "thumb_360_h" : 166,
            "permalink" : "https://nugit.slack.com/files/U024ZH7HL/F8CMV2GTH/image_uploaded_from_ios.jpg",
            "comments_count" : 5,
            "mimetype" : "image/jpeg",
            "thumb_480" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_480.jpg",
            "thumb_360_w" : 360,
            "original_w" : 1380,
            "editable" : false,
            "thumb_160" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_160.jpg",
            "permalink_public" : "https://slack-files.com/T024Z5MQM-F8CMV2GTH-e2fa366d26",
            "thumb_960_h" : 443,
            "filetype" : "jpg"
         },
         "text" : "<@U58LY38Q6> commented on <@U024ZH7HL>’s file <https://nugit.slack.com/files/U024ZH7HL/F8CMV2GTH/image_uploaded_from_ios.jpg|Left is Singapore, right is China. The survey question is, \"I am comfortable with the idea of artificial intelligence/machines acting as...\">: for each of the labels to the left I suppose",
         "type" : "message",
         "subtype" : "file_comment",
         "is_intro" : false,
         "comment" : {
            "comment" : "for each of the labels to the left I suppose",
            "created" : 1512717461,
            "timestamp" : 1512717461,
            "is_intro" : false,
            "id" : "Fc8BLZNZ1S",
            "user" : "U58LY38Q6"
         },
         "ts" : "1512717461.000198"
      },
      {
         "ts" : "1512717349.000052",
         "comment" : {
            "id" : "Fc8CFX88GN",
            "is_intro" : false,
            "user" : "U3ACT6Z2P",
            "comment" : "hm acting as what? :sweat_smile:",
            "created" : 1512717349,
            "timestamp" : 1512717349
         },
         "text" : "<@U3ACT6Z2P> commented on <@U024ZH7HL>’s file <https://nugit.slack.com/files/U024ZH7HL/F8CMV2GTH/image_uploaded_from_ios.jpg|Left is Singapore, right is China. The survey question is, \"I am comfortable with the idea of artificial intelligence/machines acting as...\">: hm acting as what? :sweat_smile:",
         "file" : {
            "thumb_800_w" : 800,
            "title" : "Left is Singapore, right is China. The survey question is, \"I am comfortable with the idea of artificial intelligence/machines acting as...\"",
            "thumb_720_w" : 720,
            "timestamp" : 1512717302,
            "thumb_800_h" : 369,
            "size" : 429223,
            "id" : "F8CMV2GTH",
            "url_private_download" : "https://files.slack.com/files-pri/T024Z5MQM-F8CMV2GTH/download/image_uploaded_from_ios.jpg",
            "is_external" : false,
            "external_type" : "",
            "name" : "Image uploaded from iOS.jpg",
            "thumb_480" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_480.jpg",
            "permalink" : "https://nugit.slack.com/files/U024ZH7HL/F8CMV2GTH/image_uploaded_from_ios.jpg",
            "comments_count" : 5,
            "mimetype" : "image/jpeg",
            "thumb_360_h" : 166,
            "original_h" : 637,
            "thumb_480_h" : 222,
            "is_public" : true,
            "filetype" : "jpg",
            "thumb_960_h" : 443,
            "thumb_160" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_160.jpg",
            "permalink_public" : "https://slack-files.com/T024Z5MQM-F8CMV2GTH-e2fa366d26",
            "editable" : false,
            "thumb_360_w" : 360,
            "original_w" : 1380,
            "thumb_480_w" : 480,
            "display_as_bot" : false,
            "image_exif_rotation" : 1,
            "thumb_960" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_960.jpg",
            "thumb_800" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_800.jpg",
            "thumb_80" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_80.jpg",
            "pretty_type" : "JPEG",
            "channels" : [
               "C024Z5MQT"
            ],
            "thumb_1024_h" : 473,
            "thumb_720" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_720.jpg",
            "thumb_1024" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_1024.jpg",
            "public_url_shared" : false,
            "thumb_720_h" : 332,
            "thumb_360" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_360.jpg",
            "thumb_960_w" : 960,
            "username" : "",
            "user" : "U024ZH7HL",
            "mode" : "hosted",
            "ims" : [],
            "url_private" : "https://files.slack.com/files-pri/T024Z5MQM-F8CMV2GTH/image_uploaded_from_ios.jpg",
            "thumb_64" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_64.jpg",
            "created" : 1512717302,
            "groups" : [],
            "thumb_1024_w" : 1024
         },
         "subtype" : "file_comment",
         "is_intro" : false,
         "type" : "message"
      },
      {
         "file" : {
            "size" : 429223,
            "timestamp" : 1512717302,
            "thumb_800_h" : 369,
            "thumb_720_w" : 720,
            "title" : "Left is Singapore, right is China. The survey question is, \"I am comfortable with the idea of artificial intelligence/machines acting as...\"",
            "thumb_800_w" : 800,
            "name" : "Image uploaded from iOS.jpg",
            "external_type" : "",
            "is_external" : false,
            "url_private_download" : "https://files.slack.com/files-pri/T024Z5MQM-F8CMV2GTH/download/image_uploaded_from_ios.jpg",
            "id" : "F8CMV2GTH",
            "is_public" : true,
            "thumb_480_h" : 222,
            "original_h" : 637,
            "thumb_360_h" : 166,
            "mimetype" : "image/jpeg",
            "comments_count" : 5,
            "permalink" : "https://nugit.slack.com/files/U024ZH7HL/F8CMV2GTH/image_uploaded_from_ios.jpg",
            "thumb_480" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_480.jpg",
            "thumb_360_w" : 360,
            "original_w" : 1380,
            "editable" : false,
            "permalink_public" : "https://slack-files.com/T024Z5MQM-F8CMV2GTH-e2fa366d26",
            "thumb_160" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_160.jpg",
            "thumb_960_h" : 443,
            "filetype" : "jpg",
            "thumb_800" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_800.jpg",
            "thumb_960" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_960.jpg",
            "image_exif_rotation" : 1,
            "display_as_bot" : false,
            "thumb_480_w" : 480,
            "thumb_1024" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_1024.jpg",
            "thumb_720" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_720.jpg",
            "thumb_1024_h" : 473,
            "channels" : [
               "C024Z5MQT"
            ],
            "pretty_type" : "JPEG",
            "thumb_80" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_80.jpg",
            "username" : "",
            "thumb_960_w" : 960,
            "thumb_720_h" : 332,
            "thumb_360" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_360.jpg",
            "public_url_shared" : false,
            "thumb_1024_w" : 1024,
            "groups" : [],
            "created" : 1512717302,
            "thumb_64" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_64.jpg",
            "url_private" : "https://files.slack.com/files-pri/T024Z5MQM-F8CMV2GTH/image_uploaded_from_ios.jpg",
            "ims" : [],
            "mode" : "hosted",
            "user" : "U024ZH7HL"
         },
         "text" : "<@U024ZH7HL> uploaded a file: <https://nugit.slack.com/files/U024ZH7HL/F8CMV2GTH/image_uploaded_from_ios.jpg|Left is Singapore, right is China. The survey question is, \"I am comfortable with the idea of artificial intelligence/machines acting as...\">",
         "user" : "U024ZH7HL",
         "subtype" : "file_share",
         "upload_reply_to" : "5B892FB5-2AAB-41AD-ADD5-007A6E60C320",
         "ts" : "1512717302.000055",
         "display_as_bot" : false,
         "type" : "message",
         "upload" : true,
         "bot_id" : null,
         "username" : "sando"
      },
 
        {
         "type" : "message",
         "attachments" : [
            {
               "id" : 1,
               "title_link" : "https://www.fastcodesign.com/90153387/inside-pinterests-12-person-ai-team-that-is-taking-on-google",
               "from_url" : "https://www.fastcodesign.com/90153387/inside-pinterests-12-person-ai-team-that-is-taking-on-google",
               "image_height" : 250,
               "text" : "Google has hundreds of researchers working on visual machine perception. Pinterest has a fraction of that. Here’s how the pinning service could still win the race to master visual search.",
               "ts" : 1513004403,
               "service_icon" : "https://www.fastcodesign.com/apple-touch-icon.png?v=2",
               "fallback" : "Co.Design: Inside Pinterest’s 12-Person AI Team That Is Taking On Google",
               "image_bytes" : 321483,
               "image_url" : "https://images.fastcompany.net/image/upload/w_1280,f_auto,q_auto,fl_lossy/wp-cms/uploads/sites/4/2017/12/p-1-pinterest-ai-deep-dive.jpg",
               "title" : "Inside Pinterest’s 12-Person AI Team That Is Taking On Google",
               "image_width" : 444,
               "service_name" : "Co.Design"
            }
         ],
         "user" : "U0F3F6F38",
         "text" : "<https://www.fastcodesign.com/90153387/inside-pinterests-12-person-ai-team-that-is-taking-on-google>",
         "reactions" : [
            {
               "count" : 1,
               "users" : [
                  "U58LY38Q6"
               ],
               "name" : "clap"
            }
         ],
         "ts" : "1513041522.000212"
        },
 
        {
           "type" : "message",
           "bot_id" : "B0VD275DX",
           "attachments" : [
              {
                 "ts" : 1512990007,
                 "title_link" : "https://blogs.microsoft.com/on-the-issues/?p=56086",
                 "title" : "AI for Earth can be a game-changer for our planet - Microsoft on the Issues",
                 "service_name" : "Microsoft on the Issues",
                 "text" : "On the two-year anniversary of the Paris climate accord, the world’s government, civic and business leaders are coming together in Paris to discuss one of the most important issues and opportunities of our time, climate change. I’m excited to lead the Microsoft delegation at these meetings. While the experts’ warnings are dire, at Microsoft we...",
                 "thumb_height" : 576,
                 "service_icon" : "https://mscorpmedia.azureedge.net/mscorpmedia/2017/08/favicon-599dd744b8cac.jpg",
                 "thumb_width" : 1024,
                 "thumb_url" : "https://mscorpmedia.azureedge.net/mscorpmedia/2017/12/AI4Earth-1024x576.jpg",
                 "fallback" : "Microsoft on the Issues: AI for Earth can be a game-changer for our planet - Microsoft on the Issues",
                 "id" : 1,
                 "from_url" : "https://blogs.microsoft.com/on-the-issues/?p=56086"
              }
           ],
           "text" : "Test text",
           "username" : "Xapier",
           "ts" : "1513073709.000326",
           "subtype" : "bot_message"
        }
 
     ],
     "ok" : true,
     "response_metadata" : {
        "next_cursor" : ""
     },
     "pin_count" : 2 }""" 

      Future.successful(
        HttpResponse(entity = HttpEntity(`application/json`, jsonData))
      )
  }
}

class FakeOAuthHttpService extends HttpService {
  import cats.data.Kleisli, cats.implicits._
  import ContentTypes._
  import scala.concurrent.Future
  override def makeSingleRequest(implicit http: HttpExt, akkaMat: ActorMaterializer) = Kleisli{ 
    (_uri: String) ⇒ 
      val token = SlackAccessToken("test-token", "read" :: Nil)
      import io.circe._, io.circe.syntax._, io.circe.generic.auto._
      Future.successful(
        HttpResponse(entity = HttpEntity(`application/json`, token.asJson.noSpaces.toString))
      )
  }
}

