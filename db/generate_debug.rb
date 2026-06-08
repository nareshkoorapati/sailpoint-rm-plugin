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
# Debug helpers for nil/blank CSV values before calling .strip
#
def csv_line(row)
  row["__csv_line"] || "unknown"
end

def debug_value(row, key)
  value = row[key]
  printable = value.nil? ? "NULL" : value.inspect
  printf("[DEBUG] CSV line=%s column=%s value=%s\n", csv_line(row), key, printable)
  value
end

def strip_field(row, key, required: true, default: nil)
  value = debug_value(row, key)

  if value.nil?
    printf("[ERROR] CSV line=%s column=%s is NULL before .strip. Check CSV header spelling and that the column exists in this row.\n",
           csv_line(row), key)
    if required
      raise "NULL value before .strip: CSV line=#{csv_line(row)}, column=#{key}"
    else
      return default
    end
  end

  value.to_s.strip
end

#
# Processes each line in the file
#
def process_row(row)
  print("Processing #{row["name"]}")

  # Read and print every column that is later stripped.
  # required: true means the script will stop with a clear column/line message instead of NoMethodError.
  name = strip_field(row, "name")
  description = strip_field(row, "description")
  deadline = strip_field(row, "deadline")
  emailNotificationEnabled = strip_field(row, "emailNotificationEnabled")
  autoRevokeAllowed = strip_field(row, "autoRevokeAllowed")
  certifier = strip_field(row, "certifier", required: false, default: "")
  certifierType = strip_field(row, "certifierType", required: false, default: "")
  type = strip_field(row, "type")
  identityQuery = strip_field(row, "identityQuery", required: false, default: nil)
  entitlementQuery = strip_field(row, "entitlementQuery", required: false, default: nil)
  recommendationsEnabled = strip_field(row, "recommendationsEnabled", required: false, default: "")
  mandatoryCommentRequirement = strip_field(row, "mandatoryCommentRequirement", required: false, default: nil)

  #
  # Debugging
  # row.each do |key, value|
  #   puts "\t #{key} : #{value}"
  # end

  #
  # Step 1 - do not create a campaign if there are no top level results to certify
  # - run the top level query, and check the count of results
  #

  if (type.downcase == "identity")
    query_json = {
      "queryType": "SAILPOINT",
      "query": {
        "query": identityQuery,
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
  elsif (type.downcase == "access")
    query_json = {
      "queryType": "SAILPOINT",
      "query": {
        "query": entitlementQuery,
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
    if (!identityQuery.nil?)
      if (!(identityQuery === "*") && type.downcase == "access")
        offset = 0
        limit = 500

        identity_query_json = {
          "queryType": "SAILPOINT",
          "query": {
            "query": identityQuery,
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
    if type.downcase == "identity"
      accessInclusionList = []

      #Skip this search if the value is nil or *
      if (!entitlementQuery.nil?)
        if !(entitlementQuery === "*")
          limit = 500

          entitlement_query_json = {
            "queryType": "SAILPOINT",
            "query": {
              "query": entitlementQuery,
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
    if (row["certifierType"] && certifierType.downcase == "governance_group")

      # Governance Group ID lookup
      offset = 0
      limit = 1
      response = nil

      if (!certifier.nil?)
        certifierQuery = certifier
        response = api_get("#{$baseUrl}/beta/workgroups?offset=#{offset}&limit=#{limit}&filters=name eq \"#{certifierQuery}\"", $token)

        if response.nil? || response.empty?
          puts "ERROR: certifier governance group query returned 0 results, this should be 1. please verify your Governance Group name."
          certifier = nil
        else
          if (JSON.parse(response).length != 1)
            puts "ERROR: certifier governance group query returned " + JSON.parse(response).length + " results, this should be 1. please verify your Governance Group name."
          end
          certifier = JSON.parse(response)[0]["id"]
          puts "\t Certifier governance group found, with id: #{certifier}"
        end
      end
    else
      # Identity search for certifier

      #only run the query if this is not a manager certification
      if (!(certifier.downcase == "manager") && !(certifier == ""))
        offset = 0
        limit = 1

        response = nil

        if (!certifier.nil?)
          certifier_query_json = {
            "queryType": "SAILPOINT",
            "includeNested": false,
            "query": {
              "query": certifier,
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
          certifier = nil
        else
          if (JSON.parse(response).length != 1)
            puts "ERROR: certifier query returned " + JSON.parse(response).length + " results, this should be 1. please refine your query."
          end
          certifier = JSON.parse(response)[0]["id"]
          puts "\t Certifier found, with id: #{certifier}"
        end
      end
    end

    #
    # Step 5 - Validate the Deadline entered, error if not in YYYY-MM-DD or YYYY/MM/DD format
    #        - this will throw an exception on an invalid date format.
    #
    parsedDate = DateTime.parse(deadline)
    sanitizedDate = parsedDate.year.to_s + "-" + parsedDate.month.to_s.rjust(2, "0") + "-" + parsedDate.day.to_s.rjust(2, "0")

    #
    # Step 6 - Build the certification, and create.
    #

    #make a deep copy of the default config
    config = Marshal.load(Marshal.dump($config["defaults"]))

    config["name"] = name
    config["description"] = description

    if (certifier.downcase == "manager" || certifier == "")
      config["searchCampaignInfo"].delete("reviewer")
    else
      config["searchCampaignInfo"]["reviewer"]["id"] = certifier
    end

    if (row["certifierType"] && certifierType.downcase == "governance_group")
      config["searchCampaignInfo"]["reviewer"]["type"] = "GOVERNANCE_GROUP"
    end

    config["deadline"] = sanitizedDate + "T06:00:00.000Z"
    config["autoRevokeAllowed"] = autoRevokeAllowed.to_s == "true"
    config["emailNotificationEnabled"] = emailNotificationEnabled.to_s == "true"
    config["recommendationsEnabled"] = recommendationsEnabled.to_s == "true"
    config["searchCampaignInfo"]["type"] = type.upcase

    if !(mandatoryCommentRequirement === nil)
      config["mandatoryCommentRequirement"] = mandatoryCommentRequirement.to_s.upcase
    end

    if type.downcase == "access"
      config["searchCampaignInfo"]["query"] = entitlementQuery.to_s
    elsif type.downcase == "identity"
      config["searchCampaignInfo"]["query"] = identityQuery.to_s
    else
      puts "ERROR: unexpected type defined: #{row["type"]}"
      end_program
    end

    #Only use the identityID filter on type Access if the query exists
    if type.downcase == "access"
      if (!identityQuery.nil?)
        if !(identityQuery === "*")
          config["searchCampaignInfo"]["identityIds"] = filteredIDs
        end
      end
    end
    #Only use the accessConstraints filter on type Access if the query exists
    if type.downcase == "identity"
      if (!entitlementQuery.nil?)
        if !(entitlementQuery === "*")
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
  CSV.read(file, headers: true, col_sep: ",").each_with_index do |row, index|
    row_hash = row.to_hash
    row_hash["__csv_line"] = index + 2 # +1 for zero-based index, +1 for header row
    process_row(row_hash)
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
