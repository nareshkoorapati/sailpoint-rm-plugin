#!/usr/bin/env ruby
require "csv"
require "json"
require_relative "lib/common.rb"
require "uri"
require "net/http"
require "date"

#
# Program Start / Version
#
start_program("IdentityNow Bulk Certification Tool", "Neil McGlennon (neil.mcglennon@sailpoint.com)", "1.6.0", "2025-02-18")

#
# Normal configration loading.
#
$config = JSON.parse(File.read(File.join(__dir__, File.join("config", "config.json"))))
puts " Getting config..."

#
# Attempt to get Personal Access Token to authenticate. Abort on failure.
#
$baseUrl = $config["baseUrl"]
clientId = $config["clientId"]
clientSecret = $config["clientSecret"]

$skipEmptyTopLevelSearches = $config["skipEmptyTopLevelSearches"]

$token = nil

if !$baseUrl.empty?
  $token = getToken(clientId, clientSecret, $baseUrl)
else
  puts "No org URL provided. Aborting Script"
  abort
end

if $token.nil?
  puts "Unable to get token. Aborting Script."
  abort
end

#
# Debug helpers for nil .strip issues
#
def debug_strip(row, key, location)
  value = row[key]
  if value.nil?
    printf("DEBUG NULL before strip | location=%s | column=%s | row_name=%s | full_row=%s\n", location, key, row["name"].inspect, row.inspect)
    return ""
  else
    printf("DEBUG before strip | location=%s | column=%s | value=%s\n", location, key, value.inspect)
    return value.to_s.strip
  end
end

def debug_downcase(row, key, location)
  value = row[key]
  if value.nil?
    printf("DEBUG NULL before downcase | location=%s | column=%s | row_name=%s | full_row=%s\n", location, key, row["name"].inspect, row.inspect)
    return ""
  else
    printf("DEBUG before downcase | location=%s | column=%s | value=%s\n", location, key, value.inspect)
    return value.to_s.downcase
  end
end

#
# Processes each line in the file
#
def process_row(row)
  print("Processing #{row["name"]}")

  #
  # Debugging
  # row.each do |key, value|
  #   puts "\t #{key} : #{value}"
  # end

  #
  # Step 1 - do not create a campaign if there are no top level results to certify
  # - run the top level query, and check the count of results
  #

  if (debug_strip(row, "type", "Step 1 type check").downcase == "identity")
    query_json = {
      "queryType": "SAILPOINT",
      "query": {
        "query": debug_strip(row, "identityQuery", "identityQuery"),
      },
      "indices": [
        "identities",
      ],
      "includeNested": false,
      "sort": [
        "id",
      ],
    }
    top_level_query_has_results = check_query_returns_items($baseUrl, $token, query_json)
  elsif (debug_strip(row, "type", "Step 1 type check").downcase == "access")
    query_json = {
      "queryType": "SAILPOINT",
      "query": {
        "query": debug_strip(row, "entitlementQuery", "entitlementQuery"),
      },
      "indices": [
        "accessprofiles",
        "entitlements",
        "roles",
      ],
      "includeNested": false,
      "sort": [
        "id",
      ],
    }
    top_level_query_has_results = check_query_returns_items($baseUrl, $token, query_json)
  end

  # Debugging
  # puts "the top level query will return at least one record: #{top_level_query_has_results}"

  if ((top_level_query_has_results === false) && ($skipEmptyTopLevelSearches === true))
    puts "*****"
    puts "*****"
    puts "*****"
    puts "***** Skipping Certification \"#{row["name"]}\", as there are 0 results from the query"
    puts "*****"
    puts "*****"
    puts "*****"
  else

    #
    # Step 2 - Validate the Identity Search Query
    # - We don't need to iterate through all results.  Just make sure its a valid query.
    #
    if (row["identityQuery"])
      if (!(debug_strip(row, "identityQuery", "identityQuery") === "*") && debug_strip(row, "type", "Step 1 type check").downcase == "access")
        offset = 0
        limit = 500

        identity_query_json = {
          "queryType": "SAILPOINT",
          "query": {
            "query": debug_strip(row, "identityQuery", "identityQuery"),
          },
          "includeNested": false,
          "sort": [
            "id",
          ],
        }

        # Debugging
        # puts "Request: #{identity_query_json.to_json}"

        #Make a list if the Identity IDs returned
        filteredIDs = []

        puts "Validating the Identity Query"

        retrieve_identities($baseUrl, $token, limit, filteredIDs, identity_query_json)

        # debugging
        if !filteredIDs.empty?
          puts "\tIdentity Query validated."
          puts "\tIdentity list size is: #{filteredIDs.length()}"

          # Debugging
          # puts "The Identity List results are: #{filteredIDs.to_json}"
        end
      end
    end

    #
    # Step 3 - Validate the Entitlement Search Query.
    #  - Iterate through access list.
    #
    if debug_strip(row, "type", "Step 1 type check").downcase == "identity"
      accessInclusionList = []

      #Skip this search if the value is nil or *
      if (row["entitlementQuery"])
        if !(row["entitlementQuery"] === "*")
          limit = 500

          entitlement_query_json = {
            "queryType": "SAILPOINT",
            "query": {
              "query": row["entitlementQuery"],
            },
            "queryResultFilter": {
              "includes": ["id"],
            },
            "sort": ["id"],
          }

          # Begin entitlement iteration. Add found entitlements to the access list.
          retrieve_access($baseUrl, $token, "entitlements", limit, accessInclusionList, entitlement_query_json)

          # Begin Access Profile iteration. Add found access profiles to the access list.
          retrieve_access($baseUrl, $token, "accessprofiles", limit, accessInclusionList, entitlement_query_json)

          #Begin Role Iteration. Add found roles to the access list.
          retrieve_access($baseUrl, $token, "roles", limit, accessInclusionList, entitlement_query_json)

          if !accessInclusionList.empty?
            puts "\t Entitlement Query validated."
            puts "access list size is: #{accessInclusionList.length()}"

            # Debugging
            # puts "The Access List results are: #{accessInclusionList.to_json}"
          end
        end
      end
    end

    #
    # Step 4 - Validate the certifier
    #

    #switch based on the Type of reviewer selected
    if (row["certifierType"] && debug_strip(row, "certifierType", "Step 4 certifierType").downcase == "governance_group")

      # Governance Group ID lookup
      offset = 0
      limit = 1
      response = nil

      if (!row["certifier"].nil?)
        certifierQuery = row["certifier"]
        response = api_get("#{$baseUrl}/beta/workgroups?offset=#{offset}&limit=#{limit}&filters=name eq \"#{certifierQuery}\"", $token)

        if response.nil? || response.empty?
          puts "ERROR: certifier governance group query returned 0 results, this should be 1. please verify your Governance Group name."
          row["certifier"] = nil
        else
          if (JSON.parse(response).length != 1)
            puts "ERROR: certifier governance group query returned " + JSON.parse(response).length + " results, this should be 1. please verify your Governance Group name."
          end
          row["certifier"] = JSON.parse(response)[0]["id"]
          puts "\t Certifier governance group found, with id: #{row["certifier"]}"
        end
      end
    else
      # Identity search for certifier

      #only run the query if this is not a manager certification
      if (!(debug_strip(row, "certifier", "Step 4/6 certifier").downcase == "manager") && !(row["certifier"] == ""))
        offset = 0
        limit = 1

        response = nil

        if (!row["certifier"].nil?)
          certifier_query_json = {
            "queryType": "SAILPOINT",
            "includeNested": false,
            "query": {
              "query": row["certifier"],
            },
          }

          # Debugging
          # puts "Request: #{certifier_query_json.to_json}"

          response = api_post_json("#{$baseUrl}/v3/search/identities?offset=#{offset}&limit=#{limit}", $token, certifier_query_json)

          response = response.body
        end

        # Debugging
        # puts "Response: #{response}"

        if response.nil? || response.empty?
          row["certifier"] = nil
        else
          if (JSON.parse(response).length != 1)
            puts "ERROR: certifier query returned " + JSON.parse(response).length + " results, this should be 1. please refine your query."
          end
          row["certifier"] = JSON.parse(response)[0]["id"]
          puts "\t Certifier found, with id: #{row["certifier"]}"
        end
      end
    end

    #
    # Step 5 - Validate the Deadline entered, error if not in YYYY-MM-DD or YYYY/MM/DD format
    #        - this will throw an exception on an invalid date format.
    #
    parsedDate = DateTime.parse(debug_strip(row, "deadline", "Step 5 deadline"))
    sanitizedDate = parsedDate.year.to_s + "-" + parsedDate.month.to_s.rjust(2, "0") + "-" + parsedDate.day.to_s.rjust(2, "0")

    #
    # Step 6 - Build the certification, and create.
    #

    #make a deep copy of the default config
    config = Marshal.load(Marshal.dump($config["defaults"]))

    config["name"] = debug_strip(row, "name", "Step 6 campaign name")
    config["description"] = debug_strip(row, "description", "Step 6 description")

    if (debug_strip(row, "certifier", "Step 4/6 certifier").downcase == "manager" || row["certifier"] == "")
      config["searchCampaignInfo"].delete("reviewer")
    else
      config["searchCampaignInfo"]["reviewer"]["id"] = debug_strip(row, "certifier", "Step 6 reviewer id")
    end

    if (row["certifierType"] && debug_strip(row, "certifierType", "Step 4 certifierType").downcase == "governance_group")
      config["searchCampaignInfo"]["reviewer"]["type"] = "GOVERNANCE_GROUP"
    end

    config["deadline"] = sanitizedDate + "T06:00:00.000Z"
    config["autoRevokeAllowed"] = debug_strip(row, "autoRevokeAllowed", "Step 6 autoRevokeAllowed").to_s == "true"
    config["emailNotificationEnabled"] = debug_strip(row, "emailNotificationEnabled", "Step 6 emailNotificationEnabled").to_s == "true"
    config["recommendationsEnabled"] = debug_strip(row, "recommendationsEnabled", "Step 6 recommendationsEnabled").to_s == "true"
    config["searchCampaignInfo"]["type"] = debug_strip(row, "type", "Step 6 campaign type").upcase

    if !(row["mandatoryCommentRequirement"] === nil)
      config["mandatoryCommentRequirement"] = debug_strip(row, "mandatoryCommentRequirement", "Step 6 mandatoryCommentRequirement").to_s.upcase
    end

    if debug_strip(row, "type", "Step 1 type check").downcase == "access"
      config["searchCampaignInfo"]["query"] = debug_strip(row, "entitlementQuery", "entitlementQuery").to_s
    elsif debug_downcase(row, "type", "Step 6 identity type check") == "identity"
      config["searchCampaignInfo"]["query"] = debug_strip(row, "identityQuery", "identityQuery").to_s
    else
      puts "ERROR: unexpected type defined: #{row["type"]}"
      end_program
    end

    #Only use the identityID filter on type Access if the query exists
    if debug_strip(row, "type", "Step 1 type check").downcase == "access"
      if (row["identityQuery"])
        if !(debug_strip(row, "identityQuery", "identityQuery") === "*")
          config["searchCampaignInfo"]["identityIds"] = filteredIDs
        end
      end
    end
    #Only use the accessConstraints filter on type Access if the query exists
    if debug_strip(row, "type", "Step 1 type check").downcase == "identity"
      if (row["entitlementQuery"])
        if !(debug_strip(row, "entitlementQuery", "entitlementQuery") === "*")
          config["searchCampaignInfo"]["accessConstraints"] = accessInclusionList
        end
      end
    end

    if config["searchCampaignInfo"]["accessConstraints"] === nil
      config["searchCampaignInfo"].delete("accessConstraints")
    end

    # Debugging
    # puts config.to_json

    response = api_post_json("#{$baseUrl}/v3/campaigns", $token, config)
    # Debugging
    # puts response.body

    result = JSON.parse(response.body)

    if response.body
      File.write("output.csv", "\"#{result["id"]}\",\"#{result["name"]}\"\n", mode: "a")
    end
  end
end

#
# Processes the whole file
#
def process_file(file)
  File.write("output.csv", "id,campaignName\n")
  CSV.read(file, headers: true, col_sep: ",").each do |row|
    process_row(row.to_hash)
  end
end

#
# Main Process
#
process_file($config["input"])

#
# Completion!
#
end_program
