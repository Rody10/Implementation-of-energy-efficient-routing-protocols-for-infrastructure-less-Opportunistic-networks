import sys


def cleanFile(contentsList,energyList):
    flagDetected = False; # found '[43200]' meaning start of energy values
    for elem in contentsList:
        if (flagDetected == True):
            energyList.append(elem)
        if (elem == '[43200]'):
            flagDetected = True
    return energyList


def calculateAverageRemainingEnergy(cleanedEnergyValuesList,numNodes):
    sum = 0
    x = range(2,len(cleanedEnergyValuesList),2)
    for elem in x:
        sum = sum + float(cleanedEnergyValuesList[elem+1])

    return sum/numNodes

def calculateNumberOfDeadNodes(cleanedEnergyValuesList,energyThreshold):
    numDeadNodes = 0
    x = range(2,len(cleanedEnergyValuesList),2)
    for elem in x:
        if ((float(cleanedEnergyValuesList[elem+1])< energyThreshold)):
            numDeadNodes = numDeadNodes+1

    return numDeadNodes
    


def main():

    energyThreshold = 600.0

    energyList = []
    numNodes = int(input('Enter number of nodes: '))
    f = open('E_ProphetRouter60nodes_EnergyLevelReport.txt','r')

    contents = f.read()
    contentsList = contents.split()

    cleanedEnergyValuesList = cleanFile(contentsList,energyList)

    averageRemainingEnergy = calculateAverageRemainingEnergy(cleanedEnergyValuesList,numNodes)
    numDeadNodes = calculateNumberOfDeadNodes(cleanedEnergyValuesList,energyThreshold)

    print('Average remaining energy = ' + str(round(averageRemainingEnergy,2)))
    print('Number of dead nodes = '+ str(numDeadNodes))
    


if __name__ == "__main__":
    main()





