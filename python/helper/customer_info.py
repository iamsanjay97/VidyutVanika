
list_of_targeted_customers = ['DowntownOffices', 'EastsideOffices', 'Village 2 NS Controllable', 'Village 1 NS Controllable', 'CentervilleHomes', 'sf2',
              'Village 2 SS Controllable', 'OfficeComplex 2 NS Controllable', 'Village 1 NS Base', 'Village 2 NS Base', 'OfficeComplex 1 NS Controllable',
              'FrostyStorage', 'Village 1 RaS Base', 'OfficeComplex 2 SS Base', 'Village 2 SS Base', 'Village 1 ReS Controllable', 'Village 1 SS Controllable',
              'OfficeComplex 1 NS Base', 'Village 1 RaS Controllable', 'seafood-2', 'sf3', 'OfficeComplex 1 SS Controllable', 'Village 2 RaS Controllable',
              'HextraChemical', 'freezeco-1', 'fc3', 'seafood-1', 'Village 1 SS Base', 'Village 2 RaS Base', 'BrooksideHomes', 'fc2', 'Village 1 ReS Base',
              'OfficeComplex 2 NS Base', 'Village 2 ReS Controllable', 'Village 2 ReS Base', 'freezeco-2', 'MedicalCenter-1', 'OfficeComplex 2 SS Controllable',
              'OfficeComplex 1 SS Base', 'freezeco-3']

list_of_producers = ['WindmillCoOp-1', 'WindmillCoOp-2', 'MedicalCenter-2', 'SolarLeasing', 'SunnyhillSolar1', 'SunnyhillSolar2']

# list_of_targeted_customers = ['BrooksideHomes', 'CentervilleHomes', 'DowntownOffices', 'EastsideOffices']

class CustomerInfo(object):

    def __init__(self):
        print('')

    def get_all_customers(self):
        return (list_of_targeted_customers+list_of_producers)

    def get_targeted_customers(self):
        return list_of_targeted_customers

    def get_list_of_producers(self):
        return list_of_producers
