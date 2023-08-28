# files
composetemplate = open("docker-compose-template.yml", "r").read()
servicetemplate = open("docker-service-template.yml", "r").read()
output = open("docker-compose.yml", "w")

numinstances = 25

# write the compose template
print(composetemplate, file = output)

# write instances
for i in range(numinstances):
    print("Generating", i)
    toWrite = servicetemplate.replace("{index}", str(i)).replace("{ip_index}", str(i + 4)).replace("{port_index}", str(2000 + i))
    print("Writing", toWrite.split("\n")[0])
    print(toWrite, file = output)