input_list = [5, 1, 4, 6, 2, 7, 3, 8]

def merge_sort(list):
    if len(list) <= 1:
        return list
            
    mid = len(list) // 2 

    left_half = merge_sort(list[:mid])
    right_half = merge_sort(list[mid:])

    result = []
    i = j = 0
    while i < len(left_half) and j < len(right_half):
        if left_half[i] < right_half[j]:
            result.append(left_half[i])
            i += 1
        else:
            result.append(right_half[j])
            j += 1

    result.extend(left_half[i:])
    result.extend(right_half[j:])

    return result


sorted_list = merge_sort(input_list)
print(sorted_list)











    
